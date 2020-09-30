package io.nebula.platform.khala.plugin.resolve.arsc

class TablePackage {
    var header: ChunkHeader? = null
    var id: Int = 0
    var name: Array<Char> = Array(16) { '0' }
    var typeStrings : Int = 0
    var lastPublicType:Int = 0
    var keyStrings:Int = 0
    var lastPublicKey: Int = 0
}