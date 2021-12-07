package rip.deadcode.request_viewer

import com.google.common.html.HtmlEscapers
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.AbstractHandler
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets


fun main() {

    // Use stdout for logging
    System.setProperty("org.slf4j.simpleLogger.logFile", "System.out")

    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080

    val server = Server(port).also {
        it.handler = Handler()
        it.setAttribute("org.eclipse.jetty.server.Request.maxFormContentSize", "1000000")
    }

    server.start()
}

class Handler : AbstractHandler() {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun handle(
        target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse
    ) {
        logger.info("Request received: {}", request)

        // Special treatment for predefined paths
        if (request.requestURI == "/favicon.ico" || request.requestURI.startsWith("/.well-known/")) {
            response.status = 404
            baseRequest.isHandled = true
            return
        }

        val requestModel = RequestModel(
            request.method,
            baseRequest.originalURI,
            request.headerNames.asSequence().map { Pair(it, request.getHeaders(it).asSequence().toList()) }.toList(),
            request.inputStream.readAllBytes().toString(StandardCharsets.UTF_8)
        )

        val responseStr = asHtml(requestModel)
        response.status = 200
        response.contentType = "text/html; charset=UTF-8"
        response.outputStream.write(responseStr.toByteArray())
        baseRequest.isHandled = true
    }
}

data class RequestModel(
    val method: String, val url: String, val headers: List<Pair<String, List<String>>>, val body: String
)

private val escaper = HtmlEscapers.htmlEscaper()

fun asHtml(model: RequestModel): String = """
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.1.3/dist/css/bootstrap.min.css" rel="stylesheet"
              integrity="sha384-1BmE4kWBq78iYhFldvKuhfTAU6auU8tT94WrHftjDbrCEXSU1oBoqyl2QvZ6jIW3" crossorigin="anonymous">
    </head>
    <body>
    <div class="container">
    
    <h1 class="mt-5">Request Viewer</h1>
    
    <h2 class="mt-5">${escaper.escape(model.method)} ${escaper.escape(model.url)}</h2>
    
    <h2 class="mt-5">Headers</h2>
    <table class="table">
    <thead>
        <tr>
            <th scope="col">Name</th>
            <th scope="col">Value</th>
        </tr>
    </thead>
    ${
    model.headers
        .flatMap { tup -> tup.second.mapIndexed { i, v -> Pair(if (i == 0) tup.first else "", v) } }
        .map { """<tr><td>${escaper.escape(it.first)}</td><td>${escaper.escape(it.second)}</td></tr>""" }
        .joinToString("")
}
    </table>
    
    <h2 class="mt-5">Body</h2>
    <pre>
    ${escaper.escape(model.body)}
    </pre>
    <p>${escaper.escape("<EOF>")}</p>
    
    </div>
    </body>
    </html>
""".trimIndent()
