import java.io.InputStream

fun getResourceAsStream(path: String): InputStream {
    return object {}.javaClass.getResource(path)!!.openStream()
}