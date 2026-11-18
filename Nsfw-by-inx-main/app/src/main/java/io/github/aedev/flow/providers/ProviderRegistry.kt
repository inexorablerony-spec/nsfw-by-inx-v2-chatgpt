package io.github.aedev.flow.providers

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

data class ProviderRegistryState(
    val providers: List<ProviderInfo> = emptyList()
)

object ProviderRegistry {
    private const val KEY_ENABLED = "enabled"
    private const val KEY_REGISTERED = "registered"
    
    private val allProviders = ConcurrentHashMap<String, Provider>()
    private val enabledProviders = ConcurrentHashMap.newKeySet<String>()
    private val knownProviderIds = ConcurrentHashMap.newKeySet<String>()
    private val registrationOrder = mutableListOf<String>()
    private var prefs: SharedPreferences? = null
    private var initialized = false
    
    private val _state = MutableStateFlow(ProviderRegistryState())
    val state: StateFlow<ProviderRegistryState> = _state.asStateFlow()
    
    @Synchronized
    fun init(ctx: Context) {
        if (initialized) return
        prefs = ctx.getSharedPreferences("providers", Context.MODE_PRIVATE)
        
        loadPreferences()
        
        if (allProviders.isEmpty()) {
            register(EpornerProvider())
            register(WebViewProvider(ctx))
        }
        
        updateState()
        initialized = true
    }
    
    private fun loadPreferences() {
        val savedEnabled = prefs?.getStringSet(KEY_ENABLED, null)
        val savedRegistered = prefs?.getStringSet(KEY_REGISTERED, null)
        
        if (savedEnabled != null) {
            enabledProviders.clear()
            enabledProviders.addAll(savedEnabled)
        }
        
        if (savedRegistered != null) {
            knownProviderIds.clear()
            knownProviderIds.addAll(savedRegistered)
        } else if (savedEnabled != null) {
            knownProviderIds.addAll(listOf("eporner", "webview"))
        }
    }
    
    private fun savePreferences() {
        val enabledCopy = enabledProviders.toSet()
        val registeredCopy = knownProviderIds.toSet()
        prefs?.edit()?.apply {
            putStringSet(KEY_ENABLED, enabledCopy)
            putStringSet(KEY_REGISTERED, registeredCopy)
        }?.apply()
    }
    
    private fun updateState() {
        val providers = registrationOrder.mapNotNull { 
            allProviders[it]?.getInfo(enabledProviders.contains(it))
        }
        _state.value = ProviderRegistryState(providers)
    }
    
    @Synchronized
    fun register(provider: Provider) {
        val isFirstRegistration = !allProviders.containsKey(provider.value)
        allProviders[provider.value] = provider
        
        if (isFirstRegistration) {
            registrationOrder.add(provider.value)
            
            if (knownProviderIds.add(provider.value)) {
                enabledProviders.add(provider.value)
            }
            
            savePreferences()
            updateState()
        }
    }
    
    @Synchronized
    fun unregister(value: String) {
        allProviders.remove(value)
        enabledProviders.remove(value)
        knownProviderIds.remove(value)
        registrationOrder.remove(value)
        savePreferences()
        updateState()
    }
    
    @Synchronized
    fun getAll(): List<Provider> = registrationOrder.mapNotNull { allProviders[it] }
    
    @Synchronized
    fun getEnabled(): List<Provider> = registrationOrder.mapNotNull { 
        allProviders[it]?.takeIf { enabledProviders.contains(it.value) }
    }
    
    @Synchronized
    fun getAllInfo(): List<ProviderInfo> = registrationOrder.mapNotNull { 
        allProviders[it]?.getInfo(enabledProviders.contains(it))
    }
    
    @Synchronized
    fun isEnabled(value: String): Boolean = enabledProviders.contains(value)
    
    @Synchronized
    fun setEnabled(value: String, enabled: Boolean) {
        if (!allProviders.containsKey(value)) return
        if (enabled) {
            enabledProviders.add(value)
        } else {
            enabledProviders.remove(value)
        }
        savePreferences()
        updateState()
    }
    
    @Synchronized
    fun getByValue(value: String): Provider? = allProviders[value]
    
    @Synchronized
    fun isInitialized(): Boolean = initialized
}
