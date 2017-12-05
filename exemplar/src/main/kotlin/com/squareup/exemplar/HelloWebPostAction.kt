package com.squareup.exemplar

import misk.web.JsonRequestBody
import misk.web.JsonResponseBody
import misk.web.Post
import misk.web.actions.WebAction
import javax.inject.Singleton

@Singleton
class HelloWebPostAction : WebAction {
    @Post("/hello/{name}")
    @JsonResponseBody
    fun hello(
        name: String,
        @JsonRequestBody body: PostBody
    ): HelloPostResponse {
        return HelloPostResponse(body.greeting, name.toUpperCase())
    }
}

data class HelloPostResponse(val greeting: String, val name: String)

data class PostBody(val greeting: String)
