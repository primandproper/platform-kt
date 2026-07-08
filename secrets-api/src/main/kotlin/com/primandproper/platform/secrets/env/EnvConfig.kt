package com.primandproper.platform.secrets.env

/**
 * Configures the [EnvSecretSource]. Port of platform-go's `env.Config` — the environment backend has
 * no provider-specific fields, so this carries none. It exists so
 * [com.primandproper.platform.secrets.SecretsConfig] can hold it symmetrically alongside the (TODO)
 * vendor configs.
 */
public class EnvConfig
