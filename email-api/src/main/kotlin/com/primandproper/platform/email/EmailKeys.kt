package com.primandproper.platform.email

/**
 * The standard observability attribute keys for outbound email, so a value is named identically
 * wherever it is recorded across backends. Port of the `Email*Key` constants in platform-go's
 * `observability/keys/keys.go` (the general keys live on `observability.Keys`; these email-specific
 * ones are carried alongside the email contract they describe).
 *
 * Note on redaction: platform-go records the recipient/sender addresses raw on the span (there is no
 * masking in the `email` backends), so this port carries that same behavior — no redaction is applied. If a
 * redaction property is ever added upstream it should be mirrored here.
 */
public object EmailKeys {
    /** The outbound email's subject. Mirrors `keys.EmailSubjectKey`. */
    public const val SUBJECT: String = "email.subject"

    /** The outbound email's recipient address. Mirrors `keys.EmailToAddressKey`. */
    public const val TO_ADDRESS: String = "email.to_address"

    /** The outbound email's sender address. Mirrors `keys.EmailFromAddressKey`. */
    public const val FROM_ADDRESS: String = "email.from_address"

    /** The provider-assigned message identifier returned by a successful send. Mirrors Go's `"email.message_id"`. */
    public const val MESSAGE_ID: String = "email.message_id"
}
