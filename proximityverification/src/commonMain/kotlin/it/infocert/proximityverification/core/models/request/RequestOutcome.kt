package it.infocert.proximityverification.core.models.request

/**
 * Inventory of what was requested from the wallet and what the wallet actually returned.
 *
 * ISO/IEC 18013-5 has no notion of a mandatory claim: an `ItemsRequest` is a wish-list — its only
 * per-element modifier, `IntentToRetain`, is a statement by the reader about its own retention
 * intentions — and a response is allowed to be partial. Requiring a claim is therefore a policy the
 * verifier applies after the fact, and this type carries the facts that policy needs.
 *
 * The SDK does not decide which claims are mandatory: it reports what happened, never a verdict.
 *
 * One entry per requested docType, in request order. A document returned without having been
 * requested does not appear here — this tree mirrors the request — but it is still visible in
 * [it.infocert.proximityverification.core.models.VerificationOutcome.DocumentReceived.documents].
 *
 * Claims are matched by exact `(namespace, identifier)`, which is what the reader put in the
 * `DeviceRequest`: in ISO/IEC 18013-5 the wallet answers with the identifiers it was given.
 *
 * When a session sends more than one request (`allowMultipleRequests`), this describes the **last**
 * response received, not the union of the session — the SDK only parses the last one.
 */
data class RequestOutcome(
    val documents: List<DocumentRequestOutcome>
)

/**
 * What happened to one requested docType.
 *
 * @property docType the requested docType.
 * @property status whether a document of this docType was returned.
 * @property errorCode the raw code the wallet declared for this docType in `documentErrors`, or
 *   `null` if it declared none. See the warning on [ClaimStatus]: this value is not authenticated.
 * @property claims the inventory of claims for this docType — requested ones plus any that arrived
 *   without being requested. Empty when the document was not returned.
 */
data class DocumentRequestOutcome(
    val docType: String,
    val status: DocumentRequestStatus,
    val errorCode: Int?,
    val claims: List<ClaimOutcome>
)

/**
 * Whether a requested document came back.
 *
 * - [RETURNED] — at least one verified document of that docType is present in the response.
 * - [DENIED] — no document, and the wallet declared the docType in `documentErrors`.
 * - [ABSENT] — no document and no declaration.
 *
 * A wallet that both declares a docType in `documentErrors` and returns a document for it is
 * reported as [RETURNED]: the verified document is the authenticated fact, the error map is not.
 */
enum class DocumentRequestStatus { RETURNED, DENIED, ABSENT }

/**
 * What happened to one claim of a returned document.
 *
 * @property nameSpace the claim namespace, e.g. `org.iso.18013.5.1`. Spelled as in the ISO/IEC
 *   18013-5 CDDL (`NameSpace`) rather than `namespace`, which Kotlin/Native would export to
 *   Objective-C as `namespace_` — the header may be included from Objective-C++, where `namespace`
 *   is a keyword — leaving every iOS consumer with a mangled name to explain.
 * @property identifier the data element identifier, e.g. `family_name`.
 * @property status see [ClaimStatus].
 * @property errorCode the raw code the wallet declared for this claim in the document's `errors`
 *   map, or `null` if it declared none. In practice this is almost always `0`, which ISO 18013-5
 *   defines as "data not returned without providing a reason": it is a placeholder, not an
 *   explanation.
 */
data class ClaimOutcome(
    val nameSpace: String,
    val identifier: String,
    val status: ClaimStatus,
    val errorCode: Int?
)

/**
 * Whether a claim came back.
 *
 * - [RETURNED] — requested and present among the issuer-signed data elements, so its value is in
 *   `DocumentVerificationResult.data` and it has been cryptographically verified.
 * - [WITHHELD] — requested, not present, and declared by the wallet in the document's `errors` map.
 * - [ABSENT] — requested, not present, and not declared.
 * - [UNREQUESTED] — present without having been requested.
 *
 * **Base a mandatory-claim decision on [RETURNED] versus not-[RETURNED], never on the difference
 * between [WITHHELD] and [ABSENT].** The `errors` and `documentErrors` maps are covered by no
 * signature whatsoever: the MSO signs the digests of the data elements that *are present*, and
 * `deviceAuth` covers `SessionTranscript`, `docType` and `DeviceNameSpacesBytes`. Neither covers the
 * error maps, so they are unauthenticated hints — useful to explain why a value is missing, unfit to
 * establish that the holder deliberately refused it. What is authenticated is the other side: a
 * [RETURNED] claim really is there and its value has been verified.
 *
 * A data element that arrives among the *device-signed* elements is reported [ABSENT], because the
 * SDK does not surface device-signed elements: reporting it as [RETURNED] would promise a value the
 * consumer cannot find anywhere. The session log warns when a response carries any.
 */
enum class ClaimStatus { RETURNED, WITHHELD, ABSENT, UNREQUESTED }
