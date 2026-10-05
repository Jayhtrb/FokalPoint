package com.fokalpoint.app.ui.viewmodel

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.Purchase
import com.fokalpoint.app.data.billing.BillingManager
import com.fokalpoint.app.data.supabase.RemoteMappers
import com.fokalpoint.app.data.supabase.SupabaseClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Creator Pro purchase flow:
 *   Play purchase → server verifies with Google (verify-play-subscription) → Pro granted
 *   in the database → app acknowledges the purchase (unacknowledged purchases are
 *   auto-refunded by Google after 3 days, so a failed verification never charges anyone).
 */
class ProViewModel(application: Application) : AndroidViewModel(application) {

    private val billing = BillingManager(application)
    private val supabase = SupabaseClient.get(application)

    val plans = billing.plans
    val billingAvailable = billing.available

    sealed class State {
        object Idle : State()
        object Working : State()
        data class Active(val proUntil: Long) : State()
        data class Error(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Set by the screen so a verified purchase can update the shared app state. */
    var onVerified: (Long) -> Unit = {}

    init {
        viewModelScope.launch { billing.loadPlans() }
        viewModelScope.launch {
            billing.events.collect { event ->
                when (event) {
                    is BillingManager.Event.Purchased -> verifyAndAcknowledge(event.purchase)
                    BillingManager.Event.Cancelled -> _state.value = State.Idle
                    is BillingManager.Event.Failed -> _state.value = State.Error(event.message)
                }
            }
        }
    }

    fun purchase(activity: Activity, plan: BillingManager.Plan) {
        val userId = supabase.auth.currentSession()?.userId ?: return
        _state.value = State.Working
        if (!billing.launchPurchase(activity, plan, userId)) {
            _state.value = State.Error("Couldn't open Google Play checkout. Please try again.")
        }
    }

    /** Restores an existing subscription (new phone, reinstall) and picks up renewals. */
    fun restore() {
        viewModelScope.launch {
            _state.value = State.Working
            val owned = billing.ownedProPurchases()
            if (owned.isEmpty()) {
                _state.value = State.Error("No Creator Pro subscription found on this Google account.")
                return@launch
            }
            owned.forEach { verifyAndAcknowledge(it) }
        }
    }

    private suspend fun verifyAndAcknowledge(purchase: Purchase) {
        _state.value = State.Working
        if (!supabase.rest.isAvailable) {
            _state.value = State.Error("Sign in with a FokalPoint account to activate Pro.")
            return
        }
        try {
            val result = supabase.rest.invokeFunction(
                "verify-play-subscription",
                JSONObject().put("purchaseToken", purchase.purchaseToken)
            )
            if (result.optBoolean("active")) {
                billing.acknowledge(purchase)
                val until = RemoteMappers.parseTimestamp(result.optString("proUntil"))
                onVerified(until)
                _state.value = State.Active(until)
            } else {
                _state.value = State.Error("This subscription isn't active.")
            }
        } catch (e: Exception) {
            _state.value = State.Error(e.message ?: "Couldn't verify your purchase. It will be retried.")
        }
    }

    fun clearError() { if (_state.value is State.Error) _state.value = State.Idle }

    override fun onCleared() {
        billing.close()
    }
}

@Suppress("unused")
private val keep: StateFlow<Boolean?>? = null
