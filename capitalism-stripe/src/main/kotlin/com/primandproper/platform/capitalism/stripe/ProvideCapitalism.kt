package com.primandproper.platform.capitalism.stripe

import com.primandproper.platform.capitalism.CapitalismConfig
import com.primandproper.platform.capitalism.PaymentManager
import com.primandproper.platform.capitalism.PaymentProvider
import com.primandproper.platform.capitalism.noop.NoopPaymentManager
import com.primandproper.platform.errors.newError
import com.primandproper.platform.observability.Logger
import com.primandproper.platform.observability.NoopLogger
import com.primandproper.platform.observability.NoopTracerProvider
import com.primandproper.platform.observability.TracerProvider

/**
 * Builds a [PaymentManager] for the configured provider. Port of platform-go's
 * `config.ProvideCapitalismImplementation`.
 *
 * This factory lives in `:capitalism-stripe` rather than `:capitalism-api` because it unites both
 * outcomes — the disabled/[NoopPaymentManager] path (from `:capitalism-api`) and the Stripe backend —
 * and wiring it in the API module would force a dependency cycle. It is the analog of Go's
 * `capitalism/config` package sitting above both `capitalism/noop` and `capitalism/stripe`.
 *
 * A disabled config returns a [NoopPaymentManager] (Go's `if !cfg.Enabled { return noop.NewPaymentManager() }`).
 * Otherwise the provider is resolved and dispatched; an unknown provider throws, matching Go's
 * `errors.Newf("unknown provider: %q", cfg.Provider)`.
 *
 * @param stripeConfig required when the provider is [PaymentProvider.STRIPE]; ignored otherwise.
 * @param handler optional Stripe webhook event callback, wired into the Stripe manager.
 */
public fun PaymentManager(
    config: CapitalismConfig,
    stripeConfig: StripeConfig? = null,
    handler: EventHandler? = null,
    logger: Logger = NoopLogger,
    tracerProvider: TracerProvider = NoopTracerProvider,
): PaymentManager {
    if (!config.enabled) return NoopPaymentManager

    return when (config.provider) {
        PaymentProvider.STRIPE -> {
            val sc = requireNotNull(stripeConfig) { "stripe provider requires a StripeConfig" }
            StripePaymentManager(sc, handler, logger, tracerProvider)
        }
        null -> throw newError("no payment provider selected for an enabled config")
    }
}
