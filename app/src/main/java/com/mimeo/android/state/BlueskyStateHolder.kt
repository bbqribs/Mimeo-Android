package com.mimeo.android.state

import com.mimeo.android.model.BlueskyAccountConnectionResponse
import com.mimeo.android.model.BlueskyBrowseItem
import com.mimeo.android.model.BlueskyBrowsePinResponse
import com.mimeo.android.model.BlueskyCandidateScanResponse
import com.mimeo.android.model.BlueskyCandidateSourceSelection
import com.mimeo.android.model.BlueskyOperatorStatusResponse
import com.mimeo.android.model.BlueskyPickerResponse
import com.mimeo.android.model.BlueskyScannerPreferences
import com.mimeo.android.model.BlueskySourceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class BlueskyStateHolder {

    // Status / diagnostics
    internal val _blueskyStatusLoading = MutableStateFlow(false)
    val blueskyStatusLoading: StateFlow<Boolean> = _blueskyStatusLoading.asStateFlow()

    internal val _blueskyStatusError = MutableStateFlow<String?>(null)
    val blueskyStatusError: StateFlow<String?> = _blueskyStatusError.asStateFlow()

    internal val _blueskyAccountConnection = MutableStateFlow<BlueskyAccountConnectionResponse?>(null)
    val blueskyAccountConnection: StateFlow<BlueskyAccountConnectionResponse?> = _blueskyAccountConnection.asStateFlow()

    internal val _blueskyOperatorStatus = MutableStateFlow<BlueskyOperatorStatusResponse?>(null)
    val blueskyOperatorStatus: StateFlow<BlueskyOperatorStatusResponse?> = _blueskyOperatorStatus.asStateFlow()

    // Connect / disconnect
    internal val _blueskyConnecting = MutableStateFlow(false)
    val blueskyConnecting: StateFlow<Boolean> = _blueskyConnecting.asStateFlow()

    internal val _blueskyConnectError = MutableStateFlow<String?>(null)
    val blueskyConnectError: StateFlow<String?> = _blueskyConnectError.asStateFlow()

    internal val _blueskyDisconnecting = MutableStateFlow(false)
    val blueskyDisconnecting: StateFlow<Boolean> = _blueskyDisconnecting.asStateFlow()

    internal val _blueskyConnectIsReadOnlyScope = MutableStateFlow(false)
    val blueskyConnectIsReadOnlyScope: StateFlow<Boolean> = _blueskyConnectIsReadOnlyScope.asStateFlow()

    // Browse
    internal val _blueskyBrowseItems = MutableStateFlow<List<BlueskyBrowseItem>>(emptyList())
    val blueskyBrowseItems: StateFlow<List<BlueskyBrowseItem>> = _blueskyBrowseItems.asStateFlow()

    internal val _blueskyBrowseSources = MutableStateFlow<List<BlueskySourceInfo>>(emptyList())
    val blueskyBrowseSources: StateFlow<List<BlueskySourceInfo>> = _blueskyBrowseSources.asStateFlow()

    internal val _blueskyBrowsePins = MutableStateFlow<List<BlueskyBrowsePinResponse>>(emptyList())
    val blueskyBrowsePins: StateFlow<List<BlueskyBrowsePinResponse>> = _blueskyBrowsePins.asStateFlow()

    internal val _blueskyBrowseLoading = MutableStateFlow(false)
    val blueskyBrowseLoading: StateFlow<Boolean> = _blueskyBrowseLoading.asStateFlow()

    internal val _blueskyBrowseLoadingMore = MutableStateFlow(false)
    val blueskyBrowseLoadingMore: StateFlow<Boolean> = _blueskyBrowseLoadingMore.asStateFlow()

    internal val _blueskyBrowseError = MutableStateFlow<String?>(null)
    val blueskyBrowseError: StateFlow<String?> = _blueskyBrowseError.asStateFlow()

    internal val _blueskyBrowseSourceFilter = MutableStateFlow<Int?>(null)
    val blueskyBrowseSourceFilter: StateFlow<Int?> = _blueskyBrowseSourceFilter.asStateFlow()

    internal val _blueskyBrowseQuery = MutableStateFlow("")
    val blueskyBrowseQuery: StateFlow<String> = _blueskyBrowseQuery.asStateFlow()

    internal val _blueskyBrowseNextCursor = MutableStateFlow<String?>(null)
    val blueskyBrowseNextCursor: StateFlow<String?> = _blueskyBrowseNextCursor.asStateFlow()

    internal val _blueskyBrowsePinsAvailable = MutableStateFlow(true)
    val blueskyBrowsePinsAvailable: StateFlow<Boolean> = _blueskyBrowsePinsAvailable.asStateFlow()

    // Candidate picker
    internal val _blueskyCandidatePicker = MutableStateFlow<BlueskyPickerResponse?>(null)
    val blueskyCandidatePicker: StateFlow<BlueskyPickerResponse?> = _blueskyCandidatePicker.asStateFlow()

    internal val _blueskyCandidatePickerLoading = MutableStateFlow(false)
    val blueskyCandidatePickerLoading: StateFlow<Boolean> = _blueskyCandidatePickerLoading.asStateFlow()

    internal val _blueskyCandidatePickerError = MutableStateFlow<String?>(null)
    val blueskyCandidatePickerError: StateFlow<String?> = _blueskyCandidatePickerError.asStateFlow()

    // Candidate scan
    internal val _blueskyCandidateSelection = MutableStateFlow<BlueskyCandidateSourceSelection?>(null)
    val blueskyCandidateSelection: StateFlow<BlueskyCandidateSourceSelection?> = _blueskyCandidateSelection.asStateFlow()

    internal val _blueskyCandidateScan = MutableStateFlow<BlueskyCandidateScanResponse?>(null)
    val blueskyCandidateScan: StateFlow<BlueskyCandidateScanResponse?> = _blueskyCandidateScan.asStateFlow()

    internal val _blueskyCandidateLoading = MutableStateFlow(false)
    val blueskyCandidateLoading: StateFlow<Boolean> = _blueskyCandidateLoading.asStateFlow()

    internal val _blueskyCandidateError = MutableStateFlow<String?>(null)
    val blueskyCandidateError: StateFlow<String?> = _blueskyCandidateError.asStateFlow()

    // Scanner preferences
    internal val _blueskyScannerPreferences = MutableStateFlow<BlueskyScannerPreferences?>(null)
    val blueskyScannerPreferences: StateFlow<BlueskyScannerPreferences?> = _blueskyScannerPreferences.asStateFlow()

    internal val _blueskyScannerPreferencesLoading = MutableStateFlow(false)
    val blueskyScannerPreferencesLoading: StateFlow<Boolean> = _blueskyScannerPreferencesLoading.asStateFlow()

    internal val _blueskyScannerPreferencesSaving = MutableStateFlow(false)
    val blueskyScannerPreferencesSaving: StateFlow<Boolean> = _blueskyScannerPreferencesSaving.asStateFlow()

    internal val _blueskyScannerPreferencesError = MutableStateFlow<String?>(null)
    val blueskyScannerPreferencesError: StateFlow<String?> = _blueskyScannerPreferencesError.asStateFlow()

    // Candidate save / pin
    internal val _blueskyCandidateSavingUrls = MutableStateFlow<Set<String>>(emptySet())
    val blueskyCandidateSavingUrls: StateFlow<Set<String>> = _blueskyCandidateSavingUrls.asStateFlow()

    internal val _blueskyCandidateSaveErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val blueskyCandidateSaveErrors: StateFlow<Map<String, String>> = _blueskyCandidateSaveErrors.asStateFlow()

    internal val _blueskyCandidatePinning = MutableStateFlow(false)
    val blueskyCandidatePinning: StateFlow<Boolean> = _blueskyCandidatePinning.asStateFlow()
}
