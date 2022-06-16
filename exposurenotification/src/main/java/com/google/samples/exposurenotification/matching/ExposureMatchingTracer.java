/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.samples.exposurenotification.matching;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Pair;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.common.collect.ImmutableList;
import com.google.samples.Clock;
import com.google.samples.Clock.DefaultClock;
import com.google.samples.Hex;
import com.google.samples.exposurenotification.ExposureKeyExportProto.TemporaryExposureKey.ReportType;
import com.google.samples.exposurenotification.ExposureNotificationEnums.Infectiousness;
import com.google.samples.exposurenotification.Log;
import com.google.samples.exposurenotification.nearby.TemporaryExposureKey;
import com.google.samples.exposurenotification.ble.utils.Constants;
import com.google.samples.exposurenotification.crypto.AesEcbEncryptor;
import com.google.samples.exposurenotification.crypto.CryptoException;
import com.google.samples.exposurenotification.data.AssociatedEncryptedMetadata;
import com.google.samples.exposurenotification.data.BluetoothMetadata;
import com.google.samples.exposurenotification.data.DayNumber;
import com.google.samples.exposurenotification.data.GeneratedRollingProximityId;
import com.google.samples.exposurenotification.data.RollingProximityId;
import com.google.samples.exposurenotification.data.TemporaryExposureKeySupport;
import com.google.samples.exposurenotification.data.generator.AssociatedEncryptedMetadataGenerator;
import com.google.samples.exposurenotification.data.generator.RollingProximityIdGenerator;
import com.google.samples.exposurenotification.features.ContactTracingFeature;
import com.google.samples.exposurenotification.storage.CloseableIterable;
import com.google.samples.exposurenotification.storage.CompletedMatchingRequestRecord;
import com.google.samples.exposurenotification.storage.ContactRecordDataStore;
import com.google.samples.exposurenotification.storage.ContactRecordDataStore.ContactRecord;
import com.google.samples.exposurenotification.storage.ExposureResult;
import com.google.samples.exposurenotification.storage.ExposureResultStorage;
import com.google.samples.exposurenotification.storage.ExposureWindowProto;
import com.google.samples.exposurenotification.storage.SightingRecord;
import com.google.samples.exposurenotification.storage.StorageException;
import com.google.samples.exposurenotification.storage.TekMetadataRecord;

import org.joda.time.Duration;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.google.samples.exposurenotification.ExposureNotificationEnums.Infectiousness.INFECTIOUSNESS_STANDARD;
import static com.google.samples.exposurenotification.ble.data.RollingProximityIdValidator.getValidWindowEndIntervalNumber;
import static com.google.samples.exposurenotification.ble.data.RollingProximityIdValidator.getValidWindowStartIntervalNumber;
import static com.google.samples.exposurenotification.ble.data.RollingProximityIdValidator.isSightingValid;
import static com.google.samples.exposurenotification.data.TemporaryExposureKeySupport.getRollingStartIntervalNumber;
import static java.util.concurrent.TimeUnit.MINUTES;

/**
 * Given a {@link MatchingRequest}, generates {@link ExposureResult}s from sightings seen.
 *
 * <p>This class loads {@link TemporaryExposureKey}s from the provided {@link MatchingRequest} and
 * runs matching against the {@link ContactRecordDataStore}. The following evaluations are done as
 * part of matching:
 *
 * <ul>
 *   <li>Checking sighted {@link RollingProximityId}s are seen in specified interval number or
 *       within {@link ContactTracingFeature#tkMatchingClockDriftRollingPeriods()} of either side of
 *       the interval.
 *   <li>Filtering exposure to be at least {@link TracingParams#minExposureBucketizedDuration()}.
 *   <li>Bucketing exposures to be multiples of five minutes. Implementation the previous point and
 *       this one is implemented in {@link KeyExposureEvaluator}.
 *   <li>Risk score calculation using {@link RiskScoreCalculator}.
 * </ul>
 *
 * <p>An exposure or encounter is a series of consecutive sightings of {@link RollingProximityId}s
 * generated by the same diagnosed {@link TemporaryExposureKey}.
 */
public class ExposureMatchingTracer {
    @Deprecated
    public static final String TOKEN_A = "ATOKEN";

    static final Comparator<SightingRecordWithMetadata> BY_TIME_ASCENDING =
            (sr1, sr2) -> sr1.sightingRecord().getEpochSeconds() - sr2.sightingRecord().getEpochSeconds();
    private static final SimpleDateFormat dataFormat =
            new SimpleDateFormat("MM-dd HH:mm:ss", Locale.ENGLISH);

    private final Context context;
    private final MatchingRequest matchingRequest;
    private final String instanceLogTag;
    private final TekMetadataRecord tekMetadataRecord;
    private final KeyExposureEvaluator keyExposureEvaluator;
    private volatile boolean stopRequested = false;

    @VisibleForTesting
    int diagnosisKeyCount;
    @VisibleForTesting
    int processedKeysCount;
    @VisibleForTesting
    int matchesFoundCount;

    public ExposureMatchingTracer(Context context, MatchingRequest matchingRequest) {
        this(context, matchingRequest, DefaultClock.getInstance());
    }

    @VisibleForTesting
    public ExposureMatchingTracer(
            Context context,
            MatchingRequest matchingRequest,
            Clock clock) {
        this.context = context;
        this.matchingRequest = matchingRequest;
        this.diagnosisKeyCount = 0;
        this.processedKeysCount = 0;
        this.matchesFoundCount = 0;
        this.instanceLogTag = getTag(matchingRequest);
        this.tekMetadataRecord =
                getTekMetadataRecord(
                        context, matchingRequest.packageName(), matchingRequest.signatureHash());
        this.keyExposureEvaluator =
                new KeyExposureEvaluator(
                        matchingRequest.exposureConfiguration(),
                        RiskScoreCalculator.create(clock),
                        TracingParams.builder().build(),
                        instanceLogTag,
                        tekMetadataRecord);
    }

    private String getTag(MatchingRequest matchingRequest) {
        return String.format("Matching#%04x", matchingRequest.hashCode());
    }

    /**
     * If there are still keys to be processed in {@link #trace()}, this method will stop tracing
     * after the {@link TemporaryExposureKey} that is currently being matched.
     */
    public void stopTrace() {
        stopRequested = true;
    }

    /**
     * Returns the request associated with the tracer.
     */
    public MatchingRequest getMatchingRequest() {
        return matchingRequest;
    }

    /**
     * Finds exposures to the provided {@link TemporaryExposureKey}s and stores the records of
     * exposure based on the {@link TracingParams} provided. Returns true if matches were found, false
     * otherwise.
     *
     * <p>See class description for details on what an exposure means.
     */
    public boolean trace() throws StorageException, CryptoException {
        boolean matchesFound;
        if (ContactTracingFeature.matchingWithNative()) {
            matchesFound = traceWithNative();
        } else {
            if (ContactTracingFeature.useMatchingPreFilter()) {
                matchesFound = traceWithPreFilter();
            } else {
                try (CloseableIterable<TemporaryExposureKey> temporaryExposureKeys =
                             matchingRequest.diagnosisKeys()) {
                    matchesFound = traceWithJava(temporaryExposureKeys);
                } catch (IOException e) {
                    Log.log.atSevere().withCause(e).log("Failed to read diagnosis keys");
                    matchesFound = false;
                }
            }
        }
        persistMatchingRequestRecord();
        return matchesFound;
    }

    private void persistMatchingRequestRecord() {
        Log.log.atInfo().log("%s Persisting matching request record.", instanceLogTag);
        CompletedMatchingRequestRecord completedMatchingRequestRecord =
                CompletedMatchingRequestRecord.newBuilder()
                        .setTemporaryKeysBatchHash(matchingRequest.keyFilesHash())
                        .setTimestampMillis(System.currentTimeMillis())
                        .setKeyCount(diagnosisKeyCount > 0 ? diagnosisKeyCount : processedKeysCount)
                        .setMatchesCount(matchesFoundCount)
                        .build();
        // FIXME: Record the completed matching request.
    }

    private boolean traceWithNative() throws StorageException, CryptoException {
        Log.log.atInfo().log("%s Native pre-filter started.", instanceLogTag);
        long startTime = System.currentTimeMillis();
        try (ContactRecordDataStore contactRecordDataStore = ContactRecordDataStore.open(context)) {
            List<byte[]> idList = contactRecordDataStore.getAllRawIds();
            try (MatchingJni matchingJni = new MatchingJni(context, idList.toArray(new byte[0][]))) {
                Set<TemporaryExposureKey> matchedKeyList;
                if (ContactTracingFeature.useNativeKeyParser()) {
                    matchedKeyList = matchingJni.matching(matchingRequest.diagnosisKeyFiles());
                    diagnosisKeyCount = matchingJni.getLastProcessedKeyCount();
                } else {
                    try (CloseableIterable<TemporaryExposureKey> temporaryExposureKeys =
                                 matchingRequest.diagnosisKeys()) {
                        Pair<Integer, Set<TemporaryExposureKey>> keysCountPair =
                                matchingJni.matchingLegacy(temporaryExposureKeys);
                        diagnosisKeyCount = keysCountPair.first;
                        matchedKeyList = keysCountPair.second;
                    } catch (IOException e) {
                        Log.log.atSevere().withCause(e).log("%s Failed to read diagnosis keys", instanceLogTag);
                        return false;
                    }
                }
                Log.log
                        .atInfo()
                        .log(
                                "%s Native pre-filter found %d (%d) keys with sightings out of %d scan"
                                        + " records. Spent time: %.3fs",
                                instanceLogTag,
                                matchedKeyList.size(),
                                diagnosisKeyCount,
                                idList.size(),
                                (System.currentTimeMillis() - startTime) / 1000f);
                return traceWithJava(matchedKeyList);
            }
        }
    }

    private boolean traceWithPreFilter() {
        Log.log.atInfo().log("%s Java pre-filter started.", instanceLogTag);
        try (ContactRecordDataStore contactRecordDataStore = ContactRecordDataStore.open(context);
             CloseableIterable<TemporaryExposureKey> temporaryExposureKeys =
                     matchingRequest.diagnosisKeys()) {
            RollingProximityIdGenerator.Factory idGeneratorFactory =
                    new RollingProximityIdGenerator.Factory();
            List<TemporaryExposureKey> matchedKeyList = new ArrayList<>();
            ContactRecordLookUpTable contactRecordLookUpTable =
                    ContactRecordLookUpTable.create(contactRecordDataStore);
            byte[] reusedEncryptedOutput =
                    new byte
                            [(int)
                            (ContactTracingFeature.tkRollingPeriodMultipleOfIdRollingPeriod()
                                    * ContactTracingFeature.contactIdLength())]; // byte[144 * 16] = byte[2304].
            AesEcbEncryptor aesEcbEncryptor = AesEcbEncryptor.create();
            for (TemporaryExposureKey diagnosisKey : temporaryExposureKeys) {
                diagnosisKeyCount++;
                List<GeneratedRollingProximityId> rollingProximityIds;
                // We prefilter based on all the possible RPIs to allow saving of attempted keys in the
                // actual matching.
                rollingProximityIds =
                        idGeneratorFactory
                                .getInstance(
                                        aesEcbEncryptor,
                                        diagnosisKey.getKeyData(),
                                        diagnosisKey.getRollingStartIntervalNumber(),
                                        TemporaryExposureKeySupport.getMaxPossibleRollingEndIntervalNumber(
                                                diagnosisKey),
                                        (int) ContactTracingFeature.rollingProximityIdKeySizeBytes(),
                                        ContactTracingFeature.rpikHkdfInfoString(),
                                        ContactTracingFeature.rpidAesPaddedString())
                                .generateIds(reusedEncryptedOutput);
                for (GeneratedRollingProximityId rollingProximityId : rollingProximityIds) {
                    if (contactRecordLookUpTable.find(rollingProximityId.rollingProximityId().getDirect())) {
                        matchedKeyList.add(diagnosisKey);
                        break;
                    }
                }
            }

            Log.log
                    .atInfo()
                    .log(
                            "%s Java pre-filter found %d keys with sightings out of %d total diagnosis keys",
                            instanceLogTag, matchedKeyList.size(), diagnosisKeyCount);
            return traceWithJava(matchedKeyList);
        } catch (StorageException | CryptoException | IOException e) {
            Log.log
                    .atWarning()
                    .withCause(e)
                    .log("%s Failure while running matching with java pre-filter", instanceLogTag);
        }

        return false;
    }

    @SuppressLint("WrongConstant")
    private boolean traceWithJava(Iterable<TemporaryExposureKey> diagnosisKeys)
            throws StorageException, CryptoException {
        Log.log.atInfo().log("%s Java tracing started.", instanceLogTag);
        if (ContactTracingFeature.moreLogForMatching()) {
            int keyCount = 0;
            Log.log.atInfo().log("Dump TEKs for matching");
            for (TemporaryExposureKey temporaryExposureKey : diagnosisKeys) {
                Log.log
                        .atInfo()
                        .log(
                                "%s, %d. Rolling start time:%s, rolling period:%d",
                                Constants.fromIdByteArrayToString(temporaryExposureKey.getKeyData()),
                                temporaryExposureKey.getRollingStartIntervalNumber(),
                                fromIntervalNumberToDateString(
                                        temporaryExposureKey.getRollingStartIntervalNumber()),
                                temporaryExposureKey.getRollingPeriod());
                keyCount++;
                if (keyCount > 10) {
                    Log.log.atInfo().log("Stop dumping TEKs due to meet maximum dumping size.");
                    break;
                }
            }
        }
        RollingProximityIdGenerator.Factory idGeneratorFactory =
                new RollingProximityIdGenerator.Factory();
        AssociatedEncryptedMetadataGenerator.Factory metadataGeneratorFactory =
                new AssociatedEncryptedMetadataGenerator.Factory();
        boolean foundMatches = false;
        stopRequested = false;
        try (ContactRecordDataStore contactRecordDataStore = ContactRecordDataStore.open(context);
             ExposureResultStorage exposureResultStore = ExposureResultStorage.open(context);
             AttemptedKeysDataStore attemptedKeysDataStore = AttemptedKeysDataStore.open(context)) {
            AesEcbEncryptor aesEcbEncryptor = AesEcbEncryptor.create();
            byte[] reusedEncryptedOutput =
                    new byte
                            [(int)
                            (ContactTracingFeature.tkRollingPeriodMultipleOfIdRollingPeriod()
                                    * ContactTracingFeature.contactIdLength())]; // byte[144 * 16] = byte[2304].
            ContactRecordLookUpTable contactRecordLookUpTable =
                    ContactTracingFeature.useMatchingFilter()
                            ? ContactRecordLookUpTable.create(contactRecordDataStore)
                            : ContactRecordLookUpTable.createDefault();
            byte[] tokenRoot =
                    ExposureResultStorage.encodeTokenRoot(
                            matchingRequest.packageName(),
                            matchingRequest.signatureHash(),
                            matchingRequest.token());
            byte[] packageRoot =
                    AttemptedKeysDataStore.encodePackageRoot(
                            matchingRequest.packageName(), matchingRequest.signatureHash());
            for (TemporaryExposureKey diagnosisKey : diagnosisKeys) {
                if (stopRequested) {
                    Log.log
                            .atInfo()
                            .log(
                                    "%s Matching pre-empted. Processed %d keys.", instanceLogTag, processedKeysCount);
                    return foundMatches;
                }
                ++processedKeysCount;

                if (exposureResultStore.hasResult(tokenRoot, diagnosisKey.getKeyData())) {
                    if (ContactTracingFeature.supportRevocationAndChangeStatusReportType()
                            && storeValidReportTransition(
                            exposureResultStore,
                            tokenRoot,
                            diagnosisKey,
                            tekMetadataRecord.getReportTypeWhenMissing())) {
                        Log.log
                                .atInfo()
                                .log(
                                        "%s Updated report type of matched diagnosis key because there was a valid"
                                                + " transition on the previous match.",
                                        instanceLogTag);
                    } else {
                        Log.log
                                .atInfo()
                                .log(
                                        "%s Skipping a diagnosis key because there was a previous match.",
                                        instanceLogTag);
                        Log.log
                                .atVerbose()
                                .log("%s -- Diagnosis key #%d skipped.", instanceLogTag, processedKeysCount);
                    }
                    continue;
                }
                if (attemptedKeysDataStore.rollingPeriodChanged(
                        packageRoot, diagnosisKey.getKeyData(), diagnosisKey.getRollingPeriod())) {
                    // Do not match keys that were already attempted and rolling period has changed. Rolling
                    // period should never change for a key. This is kept on a per app but NOT per-token
                    // basis.
                    Log.log
                            .atWarning()
                            .log(
                                    "%s Attempted to match TEK with a changed rolling period, skipping.",
                                    instanceLogTag);
                    continue;
                }
                if (!ContactTracingFeature.enableRecursiveTekReportType()
                        && diagnosisKey.getReportType() == ReportType.RECURSIVE_VALUE) {
                    Log.log
                            .atWarning()
                            .log(
                                    "%s Attempted to match TEK with RECURSIVE report type which is not enabled.",
                                    instanceLogTag);
                    continue;
                }
                if (diagnosisKey.getReportType() >= ReportType.REVOKED_VALUE) {
                    if (matchingRequest.token().equals(TOKEN_A)
                            && diagnosisKey.getReportType() == ReportType.REVOKED_VALUE
                            && ContactTracingFeature.storeMatchesForRevokedKeys()) {
                        Log.log.atInfo().log("%s Attemping to match TEK with revoked type.", instanceLogTag);
                    } else {
                        Log.log
                                .atWarning()
                                .log("%s Attempted to match TEK with report type >= REVOKED.", instanceLogTag);
                        continue;
                    }
                }

                // Generates all possibly valid RPIs for this key.
                List<GeneratedRollingProximityId> rollingProximityIds =
                        idGeneratorFactory
                                .getInstance(
                                        aesEcbEncryptor,
                                        diagnosisKey.getKeyData(),
                                        diagnosisKey.getRollingStartIntervalNumber(),
                                        TemporaryExposureKeySupport.getMaxPossibleRollingEndIntervalNumber(
                                                diagnosisKey),
                                        (int) ContactTracingFeature.rollingProximityIdKeySizeBytes(),
                                        ContactTracingFeature.rpikHkdfInfoString(),
                                        ContactTracingFeature.rpidAesPaddedString())
                                .generateIds(reusedEncryptedOutput);

                TemporaryExposureKey diagnosisKeyIgnoringRollingPeriod =
                        new TemporaryExposureKey.TemporaryExposureKeyBuilder()
                                .setKeyData(diagnosisKey.getKeyData())
                                .setRollingStartIntervalNumber(diagnosisKey.getRollingStartIntervalNumber())
                                .setRollingPeriod(
                                        (int) ContactTracingFeature.tkRollingPeriodMultipleOfIdRollingPeriod())
                                .setTransmissionRiskLevel(diagnosisKey.getTransmissionRiskLevel())
                                .setReportType(diagnosisKey.getReportType())
                                .build();
                Log.log.atInfo().log("%s Matching scans for all possible RPIs.", instanceLogTag);
                List<SightingRecordWithMetadata> matchingScansForAllPossibleRpis =
                        fetchValidSightings(
                                instanceLogTag,
                                metadataGeneratorFactory,
                                contactRecordDataStore,
                                contactRecordLookUpTable,
                                rollingProximityIds,
                                diagnosisKeyIgnoringRollingPeriod,
                                /*aggregateSightings=*/ false);

                if (!matchingScansForAllPossibleRpis.isEmpty()) {
                    // Key could possibly match to some RPIs, saving the rolling period with which it was
                    // attempted.
                    attemptedKeysDataStore.storeAttemptedKeyRollingPeriod(packageRoot, diagnosisKey);
                }

                Log.log
                        .atInfo()
                        .log(
                                "%s Matching scans for RPIs matching rolling period %d.",
                                instanceLogTag, diagnosisKey.getRollingPeriod());
                List<SightingRecordWithMetadata> matchingScansWithNoAggregation =
                        fetchValidSightings(
                                instanceLogTag,
                                metadataGeneratorFactory,
                                contactRecordDataStore,
                                contactRecordLookUpTable,
                                rollingProximityIds.subList(0, diagnosisKey.getRollingPeriod()),
                                diagnosisKey,
                                /*aggregateSightings=*/ false);

                List<SightingRecordWithMetadata> matchingScans;
                if (ContactTracingFeature.aggregateSightingsFromSingleScan()) {
                    matchingScans =
                            fetchValidSightings(
                                    instanceLogTag,
                                    metadataGeneratorFactory,
                                    contactRecordDataStore,
                                    contactRecordLookUpTable,
                                    rollingProximityIds.subList(0, diagnosisKey.getRollingPeriod()),
                                    diagnosisKey,
                                    /*aggregateSightings=*/ ContactTracingFeature.aggregateSightingsFromSingleScan());
                } else {
                    matchingScans = matchingScansWithNoAggregation;
                }
                ExposureResult exposureResult =
                        keyExposureEvaluator.findExposures(diagnosisKey, matchingScans);

                Log.log
                        .atInfo()
                        .log(
                                "%s Checked matches for diagnosis key #%d against %d matching scans.",
                                instanceLogTag, processedKeysCount, matchingScans.size());
                if (exposureResult == null) {
                    continue;
                }
                List<ExposureWindowProto> exposureWindows;
                if (matchingRequest.token().equals(TOKEN_A)) {
                    exposureWindows =
                            keyExposureEvaluator.findExposureWindows(
                                    diagnosisKey, matchingScansWithNoAggregation);
                    exposureResult =
                            exposureResult.toBuilder().addAllExposureWindows(exposureWindows).build();
                }
                Log.log.atInfo().log("%s Match found for a diagnosis key.", instanceLogTag);
                Log.log
                        .atVerbose()
                        .log("%s -- Diagnosis key #%d matched.", instanceLogTag, processedKeysCount);
                foundMatches = true;
                matchesFoundCount++;
                exposureResultStore.storeResult(
                        tokenRoot,
                        diagnosisKey,
                        exposureResult,
                        ContactTracingFeature.storeExposureResultsInTransaction());
            }
            if (ContactTracingFeature.storeExposureResultsInTransaction()) {
                exposureResultStore.commitStoreResultRequestsForTransaction();
            }
        }
        Log.log
                .atInfo()
                .log(
                        "%s Traced %d diagnosis keys and found %d matches.",
                        instanceLogTag, processedKeysCount, matchesFoundCount);
        return foundMatches;
    }

    @VisibleForTesting
    static boolean storeValidReportTransition(
            ExposureResultStorage exposureResultStore,
            byte[] tokenRoot,
            TemporaryExposureKey diagnosisKey,
            ReportType reportTypeWhenMissing) {
        ReportType newType =
                ExposureWindowUtils.getReportType(diagnosisKey.getReportType(), reportTypeWhenMissing);
        ExposureResult previousResult =
                exposureResultStore.getResult(tokenRoot, diagnosisKey.getKeyData());
        boolean validTransition =
                isValidTransition(previousResult.getTekMetadata().getReportType(), newType);
        if (validTransition
                && previousResult.getReportTypeTransitionCount()
                < ContactTracingFeature.allowedReportTypeTransitions()) {
            List<ExposureWindowProto> updatedWindows = new ArrayList<>();
            for (ExposureWindowProto window : previousResult.getExposureWindowsList()) {
                updatedWindows.add(
                        window.toBuilder()
                                .setTekMetadata(window.getTekMetadata().toBuilder().setReportType(newType).build())
                                .build());
            }

            ExposureResult updatedExposureResult =
                    previousResult.toBuilder()
                            .setTekMetadata(previousResult.getTekMetadata().toBuilder().setReportType(newType))
                            .setReportTypeTransitionCount(previousResult.getReportTypeTransitionCount() + 1)
                            .clearExposureWindows()
                            .addAllExposureWindows(updatedWindows)
                            .build();

            return exposureResultStore.storeResult(
                    tokenRoot, diagnosisKey, updatedExposureResult, /*holdForTransaction=*/ false);
        }
        return false;
    }

    /**
     * Returns sorted sightings with metadata for the given list of generated ids.
     */
    static List<SightingRecordWithMetadata> fetchValidSightings(
            String instanceLogTag,
            AssociatedEncryptedMetadataGenerator.Factory metadataGeneratorFactory,
            ContactRecordDataStore contactRecordDataStore,
            @Nullable ContactRecordLookUpTable recordPreprocessor,
            List<GeneratedRollingProximityId> generatedIds,
            TemporaryExposureKey diagnosisKey,
            boolean aggregateSightings)
            throws CryptoException {
        AssociatedEncryptedMetadataGenerator aemGenerator =
                metadataGeneratorFactory.getInstance(diagnosisKey);
        List<SightingRecordWithMetadata> sightingRecordsWithMetadata = new ArrayList<>();
        for (GeneratedRollingProximityId generatedId : generatedIds) {
            if (ContactTracingFeature.useMatchingFilter()
                    && !recordPreprocessor.find(generatedId.rollingProximityId().getDirect())) {
                continue;
            }
            if (ContactTracingFeature.moreLogForMatching()) {
                Log.log
                        .atInfo()
                        .log(
                                "%s TEK %s matches with ID %s:%d",
                                instanceLogTag,
                                Constants.fromIdByteArrayToString(diagnosisKey.getKeyData()),
                                Constants.fromIdByteArrayToString(generatedId.rollingProximityId().getDirect()),
                                generatedId.intervalNumber() - diagnosisKey.getRollingStartIntervalNumber());
            }
            List<SightingRecord> sightingRecords =
                    getValidSightingsForRpi(
                            instanceLogTag,
                            contactRecordDataStore,
                            diagnosisKey,
                            generatedId,
                            aggregateSightings);
            for (SightingRecord sightingRecord : sightingRecords) {
                // This is not a valid "old format" sighting record, skip it.
                if (!sightingRecord.hasRssi()
                        || !sightingRecord.hasAssociatedEncryptedMetadata()
                        || sightingRecord.getAssociatedEncryptedMetadata().size() != 4) {
                    continue;
                }
                BluetoothMetadata metadata =
                        aemGenerator.decrypt(
                                generatedId.rollingProximityId(),
                                AssociatedEncryptedMetadata.create(
                                        sightingRecord.getAssociatedEncryptedMetadata().toByteArray()));
                Log.log
                        .atInfo()
                        .log(
                                "%s Valid sighting found with TX Power=%s, confidence=%s",
                                instanceLogTag, metadata.txPower(), metadata.calibrationConfidence().name());

                if (metadata.txPower() <= ContactTracingFeature.matchingTxPowerUpperBound()
                        && metadata.txPower() >= ContactTracingFeature.matchingTxPowerLowerBound()) {
                    sightingRecordsWithMetadata.add(
                            SightingRecordWithMetadata.create(sightingRecord, metadata));
                } else {
                    Log.log
                            .atInfo()
                            .log(
                                    "%s TX Power %s is outside reasonable bounds [%s, %s].",
                                    instanceLogTag,
                                    metadata.txPower(),
                                    ContactTracingFeature.matchingTxPowerLowerBound(),
                                    ContactTracingFeature.matchingTxPowerUpperBound());
                }
            }
        }
        Collections.sort(sightingRecordsWithMetadata, BY_TIME_ASCENDING);
        return sightingRecordsWithMetadata;
    }

    /**
     * Fetches all valid sightings for a given {@link GeneratedRollingProximityId}.
     *
     * <p>A {@link RollingProximityId} sighting is only valid if it falls within a time window on
     * either side of {@link GeneratedRollingProximityId#intervalNumber()}. The time window is
     * configured in {@link ContactTracingFeature#tkMatchingClockDriftRollingPeriods()}.
     *
     * <p>Given that {@link ContactRecordDataStore} stores sightings keyed by {@link DayNumber}, and
     * it's sometimes valid to have sightings of a {@link RollingProximityId} across day boundaries,
     * this method contains logic to include all and valid sightings of the specified {@link
     * GeneratedRollingProximityId}.
     *
     * <p>Sightings are truncated to the first sightings within 2 * {@link
     * ContactTracingFeature#idRollingPeriodMinutes}, any sightings beyond that (counting from the
     * first sighting) are discarded as invalid. Legitimate clients should never transmit the RPI for
     * more than 20 minutes, hence sightings of the same RPI that are farther apart than that must be
     * replays.
     */
    private static List<SightingRecord> getValidSightingsForRpi(
            String instanceLogTag,
            ContactRecordDataStore dataStore,
            TemporaryExposureKey diagnosisKey,
            GeneratedRollingProximityId generatedId,
            boolean aggregateSightings) {
        DayNumber diagnosisKeyDayNumber = TemporaryExposureKeySupport.getDayNumber(diagnosisKey);
        List<SightingRecord> sightingRecords = new ArrayList<>();
        if (getValidWindowStartIntervalNumber(generatedId)
                < getRollingStartIntervalNumber(diagnosisKeyDayNumber)) {
            ContactRecord prevDayRecord =
                    dataStore.getRecord(
                            new DayNumber(diagnosisKeyDayNumber.getValue() - 1),
                            generatedId.rollingProximityId());
            if (prevDayRecord != null) {
                sightingRecords.addAll(prevDayRecord.getValue().getSightingRecordsList());
            }
        }
        ContactRecord todayRecord =
                dataStore.getRecord(diagnosisKeyDayNumber, generatedId.rollingProximityId());
        if (todayRecord != null) {
            sightingRecords.addAll(todayRecord.getValue().getSightingRecordsList());
        }
        if (getValidWindowEndIntervalNumber(generatedId, diagnosisKey)
                > getRollingStartIntervalNumber(diagnosisKeyDayNumber) + 1) {
            ContactRecord nextDayRecord =
                    dataStore.getRecord(
                            new DayNumber(diagnosisKeyDayNumber.getValue() + 1),
                            generatedId.rollingProximityId());
            if (nextDayRecord != null) {
                sightingRecords.addAll(nextDayRecord.getValue().getSightingRecordsList());
            }
        }

        // Sort by time ascending.
        Collections.sort(sightingRecords, (sr1, sr2) -> sr1.getEpochSeconds() - sr2.getEpochSeconds());

        if (ContactTracingFeature.moreLogForMatching()) {
            Log.log
                    .atInfo()
                    .log(
                            "%s Dump sighting record for RPI %s, %d. Rolling start time:%s",
                            instanceLogTag,
                            Constants.fromIdByteArrayToString(generatedId.rollingProximityId().getDirect()),
                            generatedId.intervalNumber(),
                            fromIntervalNumberToDateString(generatedId.intervalNumber()));
            for (SightingRecord sightingRecord : sightingRecords) {
                Log.log
                        .atInfo()
                        .log(
                                "%s Sighting time:%s rssi=%d, previous_scan_seconds=%d",
                                instanceLogTag,
                                fromDurationToDateString(
                                        Duration.standardSeconds(sightingRecord.getEpochSeconds())),
                                sightingRecord.getRssi(),
                                sightingRecord.getPreviousScanEpochSeconds());
            }
        }

        // Remove invalid sightings.
        Iterator<SightingRecord> validCheckIterator = sightingRecords.iterator();
        while (validCheckIterator.hasNext()) {
            SightingRecord sightingRecord = validCheckIterator.next();
            if (!isSightingValid(
                    generatedId, Duration.standardSeconds(sightingRecord.getEpochSeconds()), diagnosisKey)) {
                Log.log
                        .atInfo()
                        .log("%s Sighting discarded for being found outside of valid window.", instanceLogTag);
                if (ContactTracingFeature.moreLogForMatching()) {
                    Log.log
                            .atInfo()
                            .log(
                                    "%s Sighting time:%s is not valid.",
                                    instanceLogTag,
                                    fromDurationToDateString(
                                            Duration.standardSeconds(sightingRecord.getEpochSeconds())));
                }
                validCheckIterator.remove();
            }
        }

        // Nothing left to do.
        if (sightingRecords.isEmpty()) {
            return sightingRecords;
        }

        int firstSightingTime = sightingRecords.get(0).getEpochSeconds();
        // We allow for 2 * rotation period for the RPIs to be valid. RPIs outside of this
        // time (as calculated from the first sighting) are discarded.
        Iterator<SightingRecord> iterator = sightingRecords.iterator();
        while (iterator.hasNext()) {
            SightingRecord sightingRecord = iterator.next();
            if (sightingRecord.getEpochSeconds() - firstSightingTime
                    > 2 * MINUTES.toSeconds(ContactTracingFeature.idRollingPeriodMinutes())) {
                Log.log.atInfo().log("%s Sighting discarded for suspected replay.", instanceLogTag);
                if (ContactTracingFeature.moreLogForMatching()) {
                    Log.log
                            .atInfo()
                            .log(
                                    "%s Sighting time:%s discarded for suspected replay.",
                                    instanceLogTag,
                                    fromDurationToDateString(
                                            Duration.standardSeconds(sightingRecord.getEpochSeconds())));
                }
                iterator.remove();
            }
        }

        if (aggregateSightings) {
            Iterator<SightingRecord> aggregateSightingsIterator = sightingRecords.iterator();
            List<SightingRecord> aggregatedSightings = new ArrayList<>();
            int aggregateIndex = 0;
            aggregatedSightings.add(aggregateSightingsIterator.next());
            int previousSeconds = aggregatedSightings.get(0).getEpochSeconds();

            while (aggregateSightingsIterator.hasNext()) {
                SightingRecord currentSighting = aggregateSightingsIterator.next();
                int timeDelta = currentSighting.getEpochSeconds() - previousSeconds;
                if (timeDelta
                        > 1.5
                        * (ContactTracingFeature.scanTimeSeconds()
                        + ContactTracingFeature.scanTimeExtendForProfileInUseSeconds())) {
                    // New scan.
                    aggregateIndex += 1;
                    aggregatedSightings.add(currentSighting);
                }
                if (currentSighting.getRssi() > aggregatedSightings.get(aggregateIndex).getRssi()) {
                    aggregatedSightings.set(aggregateIndex, currentSighting);
                }
                previousSeconds = currentSighting.getEpochSeconds();
            }
            sightingRecords = aggregatedSightings;
        }

        if (ContactTracingFeature.moreLogForMatching()) {
            Log.log.atInfo().log("%s Dump filtered sightings", instanceLogTag);
            for (SightingRecord sightingRecord : sightingRecords) {
                Log.log
                        .atInfo()
                        .log(
                                "%s Sighting time:%s rssi=%d, previous_scan_seconds=%d",
                                instanceLogTag,
                                fromDurationToDateString(
                                        Duration.standardSeconds(sightingRecord.getEpochSeconds())),
                                sightingRecord.getRssi(),
                                sightingRecord.getPreviousScanEpochSeconds());
            }
        }

        return sightingRecords;
    }

    private static String fromIntervalNumberToDateString(int intervalNumber) {
        return fromTimeInMsToDateString(
                MINUTES.toMillis((intervalNumber * ContactTracingFeature.idRollingPeriodMinutes())));
    }

    private static String fromDurationToDateString(Duration duration) {
        return fromTimeInMsToDateString(duration.getMillis());
    }

    private static String fromTimeInMsToDateString(long timeInMs) {
        return dataFormat.format(timeInMs);
    }

    private static TekMetadataRecord getTekMetadataRecord(
            Context context, String packageName, byte[] packageSignature) {
        return new ClientRecordDataStore(context)
                .getTekMetadataRecord(packageName, packageSignature);
    }

    @VisibleForTesting
    static boolean isValidTransition(ReportType oldReportType, ReportType newReportType) {
        if (oldReportType == ReportType.SELF_REPORT) {
            return ImmutableList.of(
                    ReportType.CONFIRMED_TEST,
                    ReportType.CONFIRMED_CLINICAL_DIAGNOSIS,
                    ReportType.REVOKED)
                    .contains(newReportType);
        }

        if (oldReportType == ReportType.CONFIRMED_CLINICAL_DIAGNOSIS) {
            return ImmutableList.of(ReportType.CONFIRMED_TEST, ReportType.REVOKED)
                    .contains(newReportType);
        }

        return false;
    }

    /**
     * Stores attempted keys and their associated rolling period.
     *
     * <p>The store is keyed by PackageNameHash:Signature:TemporaryExposureKeyData.
     */
    // FIXME: Replace with a proper data store.
    private static final class AttemptedKeysDataStore implements AutoCloseable {
        public static AttemptedKeysDataStore open(Context context) throws StorageException {
            return new AttemptedKeysDataStore(context);
        }

        private AttemptedKeysDataStore(Context context) {
        }

        /**
         * Encodes package root into byte array.
         */
        public static byte[] encodePackageRoot(String packageName, byte[] signatureHash) {
            // FIXME: Use a better encoding method
            String packageRoot = String.format("{package:%s,hash:%s}",
                    packageName,
                    Hex.bytesToStringLowercase(signatureHash));
            return packageRoot.getBytes();
        }

        public void storeAttemptedKeyRollingPeriod(byte[] packageRoot, TemporaryExposureKey diagnosisKey) {
            // FIXME: Store the attempted key.
        }

        public boolean rollingPeriodChanged(byte[] packageRoot, byte[] keyData, int rollingPeriod) {
            // FIXME: Check if the rolling period has changed.
            return false;
        }

        @Override
        public void close() {
            /* nothing to do */
        }
    }

    private static final class ClientRecordDataStore implements AutoCloseable {
        private final HashMap<String, TekMetadataRecord> store = new HashMap<>();

        public ClientRecordDataStore(Context context) {
        }

        public TekMetadataRecord getTekMetadataRecord(String packageName, byte[] packageSignature) {
            String packageKey = encodePackage(packageName, packageSignature);
            if (store.containsKey(packageKey)) {
                return store.get(packageKey);
            }
            return createDefaultTekMetadataRecord();
        }

        public static TekMetadataRecord createDefaultTekMetadataRecord() {
            Infectiousness[] infectiousnessesArray = new Infectiousness[29];
            Arrays.fill(infectiousnessesArray, INFECTIOUSNESS_STANDARD);
            return TekMetadataRecord.newBuilder()
                    .addAllDaysSinceOnsetToInfectiousness(Arrays.asList(infectiousnessesArray))
                    .build();
        }

        private String encodePackage(String packageName, byte[] packageSignature) {
            // FIXME: Use a better encoding method
            return String.format("{package:%s,hash:%s}",
                    packageName,
                    Hex.bytesToStringLowercase(packageSignature));
        }

        @Override
        public void close() throws Exception {
            /* nothing to do */
        }
    }
}