package reactor.netty.http.server // Placed in foreign package because HttpPredicate is not public

import com.walkmind.extensions.netty.JsonMapper
import com.walkmind.extensions.netty.StringParser
import com.walkmind.extensions.netty.sendContentChunked
import io.netty.buffer.ByteBuf
import io.netty.handler.codec.http.*
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import reactor.core.Exceptions
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.netty.ByteBufFlux
import reactor.netty.NettyOutbound
import reactor.netty.http.websocket.WebsocketInbound
import reactor.netty.http.websocket.WebsocketOutbound
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.net.URI
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.function.BiFunction
import java.util.function.Function
import java.util.function.Predicate

abstract class TypeDefinition202211122308<T> {
    val type: Type = (javaClass.genericSuperclass as ParameterizedType).actualTypeArguments[0]
}

inline fun <reified T> javaType(): Type = object : TypeDefinition202211122308<T>() {}.type

typealias HttpInterceptor = BiFunction<MyHttpServerRequest, MyHttpServerResponse, Publisher<Void>>
typealias WSInterceptor = BiFunction<in WebsocketInbound, in WebsocketOutbound, out Publisher<Void>>

class MyHttpServerRequest(val delegate: HttpServerRequest, val context: RoutingConfig): HttpServerRequest by delegate {

    lateinit var contextValues: MutableMap<String, Any>

    fun getQueryParams(): Map<String, List<String>> {
        return QueryStringDecoder(delegate.uri()).parameters() ?: emptyMap()
    }

    inline fun <reified T> getQueryParam(key: String): T? {

        return this.getQueryParams()[key]?.firstOrNull()?.let { context.stringParser.parse(javaType<T>(), it) }
    }

    inline fun <reified T> getQueryParamOrFail(key: String): T {

        return this.getQueryParam(key) ?: error("Parameter $key is missing.")
    }

    inline fun <reified T> getParam(key: String): T? {

        return this.param(key)?.let { context.stringParser.parse(javaType<T>(), it) }
    }

    inline fun <reified T> getOrFail(key: String): T {

        return this.getParam(key) ?: error("Parameter $key is missing.")
    }

    inline fun <reified T> receiveAs(): Mono<T> {

        val type = javaType<T>() // Do not put inside Mono's lambda. Kotlin loses typ information.
        return this.receive().aggregate().asString().mapNotNull { body ->
            context.jsonMapper.deserialize(type, body)
        }
    }

    override fun receiveContent(): Flux<HttpContent> {
        return delegate.receiveContent()
    }

    fun addContextValue(name: String, value: Any) {
        if (!::contextValues.isInitialized)
            contextValues = mutableMapOf()

        contextValues[name] = value
    }

    fun <T> getContextValue(name: String): T? {
        return if (::contextValues.isInitialized)
            contextValues[name] as T?
        else
            null
    }

    fun hasContextValues(): Boolean =
        ::contextValues.isInitialized && contextValues.isNotEmpty()

    fun addContextValues(items: Map<String, Any>) {
        if (!::contextValues.isInitialized)
            contextValues = mutableMapOf()

        contextValues.putAll(items)
    }
}

class MyHttpServerResponse(val delegate: HttpServerResponse, val context: RoutingConfig): HttpServerResponse by delegate {

    inline fun <reified T : Any> sendJson(data: Mono<T>): NettyOutbound {

        val type = javaType<T>()

        return delegate
            .addHeader(HttpHeaderNames.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_JSON.toString())
            .sendString(data.mapNotNull { value ->
                context.jsonMapper.serialize(type, value)
            })
    }

    inline fun <reified T : Any> sendJson(data: T): NettyOutbound {

        return delegate
            .addHeader(HttpHeaderNames.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_JSON.toString())
            .sendString(Mono.justOrEmpty(context.jsonMapper.serialize(javaType<T>(), data)))
    }


    override fun sendWebsocket(websocketHandler: BiFunction<in WebsocketInbound, in WebsocketOutbound, out Publisher<Void>>, websocketServerSpec: WebsocketServerSpec): Mono<Void> {
        return delegate.sendWebsocket(websocketHandler, websocketServerSpec)
    }

    override fun status(status: HttpResponseStatus): HttpServerResponse {
        return delegate.status(status)
    }

    override fun neverComplete(): Mono<Void> {
        return delegate.neverComplete()
    }

    override fun send(dataStream: Publisher<out ByteBuf>): NettyOutbound {
        return delegate.send(dataStream)
    }

    override fun sendByteArray(dataStream: Publisher<out ByteArray>): NettyOutbound {
        return delegate.sendByteArray(dataStream)
    }

    override fun sendFile(file: Path): NettyOutbound {
        return delegate.sendFile(file)
    }

    override fun sendFile(file: Path, position: Long, count: Long): NettyOutbound {
        return delegate.sendFile(file, position, count)
    }

    override fun sendFileChunked(file: Path, position: Long, count: Long): NettyOutbound {
        return delegate.sendFileChunked(file, position, count)
    }

    override fun sendGroups(dataStreams: Publisher<out Publisher<out ByteBuf>>): NettyOutbound {
        return delegate.sendGroups(dataStreams)
    }

    override fun sendObject(dataStream: Publisher<*>): NettyOutbound {
        return delegate.sendObject(dataStream)
    }

    override fun sendString(dataStream: Publisher<out String>): NettyOutbound {
        return delegate.sendString(dataStream)
    }

    override fun sendString(dataStream: Publisher<out String>, charset: Charset): NettyOutbound {
        return delegate.sendString(dataStream, charset)
    }

    override fun subscribe(s: Subscriber<in Void>) {
        delegate.subscribe(s)
    }

    override fun status(status: Int): HttpServerResponse {
        return delegate.status(status)
    }

    override fun then(): Mono<Void> {
        return delegate.then()
    }

    override fun then(other: Publisher<Void>): NettyOutbound {
        return delegate.then(other)
    }

    override fun then(other: Publisher<Void>, onCleanup: Runnable): NettyOutbound {
        return delegate.then(other, onCleanup)
    }

    override fun sendWebsocket(websocketHandler: BiFunction<in WebsocketInbound, in WebsocketOutbound, out Publisher<Void>>): Mono<Void> {
        return delegate.sendWebsocket(websocketHandler)
    }

    override fun path(): String {
        return delegate.path()
    }

    override fun equals(other: Any?): Boolean {
        return delegate == other
    }

    override fun hashCode(): Int {
        return delegate.hashCode()
    }

    override fun toString(): String {
        return delegate.toString()
    }
}

fun defaultExceptionHandler(req: MyHttpServerRequest, res: MyHttpServerResponse, ex: Throwable): Mono<Void> {

    val message = "Exception on handling ${req.method()} ${req.uri()}: ${ex.message}"
    return Mono.fromDirect(res.status(HttpResponseStatus.INTERNAL_SERVER_ERROR).sendString(Mono.just(message)))
}

data class RoutingConfig(
    val jsonMapper: JsonMapper,
    val stringParser: StringParser,
    val exceptionHandler: (MyHttpServerRequest, MyHttpServerResponse, Throwable) -> Mono<Void> = ::defaultExceptionHandler
)

data class HttpRequestHandler(
    private val condition: Predicate<in HttpServerRequest>,
    private val handler: HttpInterceptor,
    private val resolver: Function<in String, Map<String, String>?> // extracts url template parameters from uri in map of (parameter name) -> (value)
) : (MyHttpServerRequest, MyHttpServerResponse) -> Publisher<Void>, Predicate<HttpServerRequest> {

    override fun invoke(request: MyHttpServerRequest, response: MyHttpServerResponse): Publisher<Void> {
        val paramsResolver = request.paramsResolver(resolver)

        val copyRequest = MyHttpServerRequest(paramsResolver, request.context)
        if (request.hasContextValues())
            copyRequest.addContextValues(request.contextValues)

        return handler.apply(copyRequest, response)
    }

    override fun test(o: HttpServerRequest): Boolean {
        return condition.test(o)
    }
}

typealias HttpTransformer = (MyHttpServerRequest, MyHttpServerResponse) -> MyHttpServerRequest?

// Short-lived class that is created once during route initialization. We collect all request handlers into MutableList<HttpRequestHandler>.
open class Route(
    private val parent: Route?,
    val segment: String,
    private val handlers: MutableList<HttpRequestHandler>,
    private val config: RoutingConfig,
    private val httpTransformer: HttpTransformer?
) {

    fun createChild(path: String, httpTransformer: HttpTransformer? = null): Route {
        return Route(this, path, handlers, config, httpTransformer)
    }

    operator fun invoke(body: Route.() -> Unit): Unit = body()

    fun addHandler(handler: HttpRequestHandler) {

        val transformers = collectNonFull { it.httpTransformer }
        if (transformers.isNotEmpty()) {

            // Pass http request instance from one transformer to another until it returns null.
            val combinedHttpTransformer: (MyHttpServerRequest, MyHttpServerResponse) -> Pair<MyHttpServerRequest, Boolean> = { req, res ->
                var finalReq : MyHttpServerRequest = req
                var terminate = false

                for (transformer in transformers) {
                    val newReq = transformer.invoke(finalReq, res)
                    if (newReq != null)
                        finalReq = newReq
                    else {
                        // If a transformation wants to terminate the request we ignore all subsequent transformations and return most recent altered request object
                        terminate = true
                        break
                    }

                }

                finalReq to terminate
            }

            val newHandler = handler.copy(handler = { req: MyHttpServerRequest, res: MyHttpServerResponse ->

                val (newReq, terminate) = combinedHttpTransformer.invoke(req, res)
                if (!terminate)
                    handler.invoke(newReq, res)
                else
                    res.send()
            })

            handlers.add(newHandler)
        } else {
            handlers.add(handler)
        }
    }

    // Full url path from top to the bottom (current route).
    fun fullPath(): String {
        val parts = collectNonFull { it.segment }
        return parts.joinToString("")
    }

    // Create single root handler object that should iterate through all sub-handlers and predicates and make decision how to handle HTTP request.
    fun mergeHandlers(): BiFunction<in HttpServerRequest, in HttpServerResponse, out Publisher<Void>> {

        return BiFunction { request, response ->
            try {

                val req = MyHttpServerRequest(request, config)
                val res = MyHttpServerResponse(response, config)

//                res.status(HttpResponseStatus.INTERNAL_SERVER_ERROR) // Just in case filter didn't set any but returned TRUE.

                // find I/0 handler to process this request
                handlers.find { it.test(request) }?.let { handler ->

                    try {
                        Mono.fromDirect(handler.invoke(req, res))
                            .onErrorResume { ex ->
                                config.exceptionHandler(req, res, ex)
                            }
                    } catch (ex: Throwable) {
                        config.exceptionHandler(req, res, ex)
                    }
                } ?: response.sendNotFound()
            } catch (ex: Throwable) {
                Exceptions.throwIfJvmFatal(ex)
                Mono.error(ex) //500
            }
        }
    }

    // Collect items of hierarchy of routes from top to the bottom (current route).
    private fun <T> collectNonFull(f: (Route) -> T?): List<T> {
        val result = LinkedList<T>()
        var cur: Route? = this
        while (cur != null) {
            val v = f(cur)
            if (v != null)
                result.addFirst(v)
            cur = cur.parent
        }
        return result
    }
}

// Root route object.
class Routing(config: RoutingConfig) : Route(parent = null, "", mutableListOf(), config, null)

fun HttpServer.routing(config: RoutingConfig, configuration: Routing.() -> Unit): HttpServer {
    val routes = Routing(config).apply(configuration)
    return this.handle(routes.mergeHandlers())
}

fun Route.route(segment: String, build: Route.() -> Unit): Route {
    return this.createChild(segment).apply(build)
}

private fun Route.addRouteHandler(condition: Predicate<HttpServerRequest>, handler: HttpInterceptor): Route {

    val httpRouteHandler =
        if (condition is HttpPredicate) {
            HttpRequestHandler(condition, handler, condition)
        } else {
            // Argument of HttpServerRequest.paramsResolver() is not annotated with @Nullable, so we can't pass null from kotlin.
            // Instead, we provide resolved that works the same way as reactor.netty.http.server.HttpServerOperations.params() works when it has null resolver.
            HttpRequestHandler(condition, handler, { null })
        }

    addHandler(httpRouteHandler)

    return this
}

fun Route.get(segment: String, handler: HttpInterceptor): Route {
    return this.addRouteHandler(HttpPredicate.get(this.fullPath() + segment), handler)
}

fun Route.post(segment: String, handler: HttpInterceptor): Route {
    return this.addRouteHandler(HttpPredicate.post(this.fullPath() + segment), handler)
}

fun Route.put(segment: String, handler: HttpInterceptor): Route {
    return this.addRouteHandler(HttpPredicate.put(this.fullPath() + segment), handler)
}

fun Route.delete(segment: String, handler: HttpInterceptor): Route {
    return this.addRouteHandler(HttpPredicate.delete(this.fullPath() + segment), handler)
}

fun Route.head(segment: String, handler: HttpInterceptor): Route {
    return this.addRouteHandler(HttpPredicate.head(this.fullPath() + segment), handler)
}

fun Route.options(segment: String, handler: HttpInterceptor): Route {
    return this.addRouteHandler(HttpPredicate.options(this.fullPath() + segment), handler)
}

fun Route.patch(segment: String, handler: HttpInterceptor): Route {
    return this.addRouteHandler(HttpPredicate.http(this.fullPath() + segment, null, HttpMethod.PATCH), handler)
}

fun Route.ws(segment: String, handler: WSInterceptor, websocketServerSpec: WebsocketServerSpec): Route {
    return this.addRouteHandler(HttpPredicate.get(this.fullPath() + segment)) { req, resp ->
        if (req.requestHeaders().containsValue(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE, true)) {
            val ops = req as HttpServerOperations
            return@addRouteHandler ops.withWebsocketSupport(req.uri(), websocketServerSpec, handler)
        }
        resp.sendNotFound()
    }
}

fun Route.resource(segment: String, resourcePackage: String): Route {

    val uri = this.fullPath() + segment
    val handler = object : HttpInterceptor {
        override fun apply(req: MyHttpServerRequest, resp: MyHttpServerResponse): Publisher<Void> {
            var prefix = URI.create(req.uri())
                .path
                .replaceFirst(uri.toRegex(), "")

            if (prefix.isNotEmpty() && prefix[0] == '/')
                prefix = prefix.substring(1)

            val items = Thread.currentThread().contextClassLoader.getResources("$resourcePackage/$prefix").asSequence()
            val url = items.map { it.toURI() }.firstOrNull()?.toURL()

            return if (url != null)
                resp.sendContentChunked(url)
            else
                resp.sendNotFound()
        }
    }
    return addRouteHandler(HttpPredicate.prefix(uri), handler)
}

fun Route.transform(httpTransformer: HttpTransformer, build: Route.() -> Unit): Route {
    return this.createChild("", httpTransformer).apply(build)
}

fun Route.file(segment: String, path: Path): Route {
    return this.addRouteHandler(HttpPredicate.get(this.fullPath() + segment)) { _, res ->
        if (Files.isReadable(path))
            res.sendFile(path)
        else
            res.send(ByteBufFlux.fromPath(path))
    }
}
