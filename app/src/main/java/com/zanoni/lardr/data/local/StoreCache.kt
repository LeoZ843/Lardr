package com.zanoni.lardr.data.local

import com.zanoni.lardr.data.model.Store
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StoreCache @Inject constructor() {
    private val cache = mutableMapOf<String, Store>()

    fun put(store: Store) {
        cache[store.id] = store
    }

    fun putAll(stores: List<Store>) {
        stores.forEach { cache[it.id] = it }
    }

    fun get(storeId: String): Store? = cache[storeId]
}