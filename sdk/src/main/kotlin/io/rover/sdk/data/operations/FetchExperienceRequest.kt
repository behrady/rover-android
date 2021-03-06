package io.rover.sdk.data.operations

import io.rover.sdk.data.domain.Experience
import io.rover.sdk.data.graphql.GraphQlRequest
import io.rover.sdk.data.operations.data.decodeJson
import org.json.JSONObject

internal class FetchExperienceRequest(
    private val queryIdentifier: ExperienceQueryIdentifier
) : GraphQlRequest<Experience> {
    override val operationName: String = "FetchExperience"

    override val mutation: Boolean
        get() = false

    override val fragments: List<String>
        get() = listOf("experienceFields")

    override val query: String
    get() {
        return when(queryIdentifier) {
            is ExperienceQueryIdentifier.ById -> {
                if (queryIdentifier.useDraft) {
                    """
                    query FetchExperienceById(${"\$"}id: ID, ${"\$"}versionID: String) {
                        experience(id: ${"\$"}id, versionID: ${"\$"}versionID) {
                            ...experienceFields
                        }
                    }
                    """.trimIndent()
                } else {
                    """
                    query FetchExperienceById(${"\$"}id: ID) {
                        experience(id: ${"\$"}id) {
                            ...experienceFields
                        }
                    }
                    """.trimIndent()
                }
            }
            is ExperienceQueryIdentifier.ByUniversalLink -> {
                """
                query FetchExperienceByCampaignURL(${"\$"}campaignURL: String) {
                    experience(campaignURL: ${"\$"}campaignURL) {
                        ...experienceFields
                    }
                }
                """.trimIndent()
            }
        }
    }
    override val variables: JSONObject = JSONObject().apply {
        when (queryIdentifier) {
            is ExperienceQueryIdentifier.ById -> {
                put("id", queryIdentifier.id)
                if(queryIdentifier.useDraft) {
                    put("versionID", "current")
                }
            }
            is ExperienceQueryIdentifier.ByUniversalLink -> {
                put("campaignURL", queryIdentifier.uri)
            }
        }
    }

    override fun decodePayload(responseObject: JSONObject): Experience {
        return Experience.decodeJson(
            responseObject.getJSONObject("data").getJSONObject("experience")
        )
    }

    sealed class ExperienceQueryIdentifier {
        /**
         * Experiences may be started by just their ID.
         *
         * (This method is typically used when experiences are started from a deep link or
         * progammatically.)
         */
        data class ById(val id: String, val useDraft: Boolean) : ExperienceQueryIdentifier()

        /**
         * Experiences may be started from a universal link.  The link itself may ultimately, but
         * opaquely, address the experience and a possibly associated campaign, but it is up to the
         * cloud API to resolve it.
         *
         * (This method is typically used when experiences are started from external sources,
         * particularly email, social, external apps, and other services integrated into the app).
         */
        data class ByUniversalLink(val uri: String) : ExperienceQueryIdentifier()
    }
}
