package io.rover.sdk.ui.blocks.poll

import android.net.Uri
import io.rover.sdk.data.graphql.ApiResult
import io.rover.sdk.data.http.HttpClient
import io.rover.sdk.data.http.HttpClientResponse
import io.rover.sdk.data.http.HttpRequest
import io.rover.sdk.data.http.HttpResultMapper
import io.rover.sdk.data.http.HttpVerb
import io.rover.sdk.logging.log
import io.rover.sdk.streams.map
import io.rover.sdk.streams.subscribe
import org.json.JSONObject
import org.reactivestreams.Publisher
import java.net.URL

internal class VotingService(
    private val endpoint: String,
    private val httpClient: HttpClient,
    private val httpResultMapper: HttpResultMapper = HttpResultMapper(),
    private val urlBuilder: URLBuilder = URLBuilder()
) {
    fun fetchResults(pollId: String, optionIds: List<String>): Publisher<ApiResult<OptionResults>> {
        val url = urlBuilder.build(endpoint, listOf(pollId), optionIds.map { "options" to it })
        val urlRequest = HttpRequest(url, hashMapOf(), HttpVerb.GET)

        return httpClient.request(urlRequest, null).map { httpClientResponse ->
            httpResultMapper.mapResultWithBody(httpClientResponse) {
                OptionResults.decodeJson(JSONObject(it))
            }
        }
    }

    fun castVote(pollId: String, optionId: String, jsonObject: JSONObject = JSONObject()): Publisher<VoteOutcome> {
        val url = urlBuilder.build(endpoint, listOf(pollId, "vote"))
        val urlRequest = HttpRequest(url, hashMapOf("Content-Type" to "application/json"), HttpVerb.POST)

        val body = jsonObject.apply {
            put("option", optionId)
        }.toString()

        return httpClient.request(urlRequest, body, false).map {
            when (it) {
                is HttpClientResponse.Success -> {
                    log.v("vote in poll $pollId with option $optionId succeeded")
                    VoteOutcome.VoteSuccess
                }
                is HttpClientResponse.ApplicationError, is HttpClientResponse.ConnectionFailure -> {
                    log.w("voting failed $it")
                    VoteOutcome.VoteFailure
                }
            }
        }
    }
}

internal sealed class VoteOutcome {
    object VoteSuccess: VoteOutcome()
    object VoteFailure: VoteOutcome()
} 

internal class URLBuilder {
    fun build(url: String, pathParams: List<String>? = null, queryParams: List<Pair<String, String>>? = null): URL {
        val uri = Uri.parse(url).buildUpon().apply {
            pathParams?.forEach { appendPath(it) }
            queryParams?.forEach { appendQueryParameter(it.first, it.second) }
        }.toString()

        return URL(uri)
    }
}
