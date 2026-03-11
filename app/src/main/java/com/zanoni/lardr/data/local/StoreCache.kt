package com.zanoni.lardr.data.local

import com.zanoni.lardr.data.model.Store
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StoreCache @Inject constructor() {

    private val _stores = MutableStateFlow<Map<String, Store>>(emptyMap())
    val flow: StateFlow<Map<String, Store>> = _stores.asStateFlow()

    fun put(store: Store) {
        _stores.value = _stores.value + (store.id to store)
    }

    fun merge(stores: List<Store>) {
        if (stores.isEmpty()) return
        _stores.value = _stores.value + stores.associateBy { it.id }
    }

    fun remove(storeId: String) {
        _stores.value = _stores.value - storeId
    }

    fun get(storeId: String): Store? = _stores.value[storeId]
}