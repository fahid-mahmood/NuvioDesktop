package com.nuvio.app.features.home.aggregate

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object AggregatedCatalogStorage {
    private val store = DesktopStorage.store("nuvio_aggregated_catalog")

    actual fun loadPayload(): String? =
        store.getString(ProfileScopedKey.of("aggregated_catalog_payload"))

    actual fun savePayload(payload: String) {
        store.putString(ProfileScopedKey.of("aggregated_catalog_payload"), payload)
    }
}
