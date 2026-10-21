package com.nuvio.app.features.home.aggregate

internal expect object AggregatedCatalogStorage {
    fun loadPayload(): String?
    fun savePayload(payload: String)
}
