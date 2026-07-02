package com.cosmicocean.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path

interface GitHubApiService {
    // Accept: application/vnd.github.raw makes the contents API return the raw file body
    @Headers("Accept: application/vnd.github.raw", "X-GitHub-Api-Version: 2022-11-28")
    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getRawFile(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String
    ): Response<ResponseBody>
}
