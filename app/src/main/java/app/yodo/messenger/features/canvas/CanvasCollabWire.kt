package app.yodo.messenger.features.canvas

import kotlinx.serialization.json.*

sealed interface CanvasWire {
    data class Snapshot(val session:String,val doc:CanvasDocument,val revision:Long,val hostActor:String,val guestActor:String):CanvasWire
    data class Propose(val session:String,val command:CanvasCommand):CanvasWire
    data class Committed(val session:String,val commit:CanvasCommit):CanvasWire
    data class Rejected(val session:String,val requestId:String,val revision:Long,val reason:String):CanvasWire
    data class Cursor(val session:String,val sequence:Long,val point:CanvasPoint?,val color:Int):CanvasWire
}
object CanvasCollabWire {
    const val MAX_BYTES=6_000_000
    // Encode objects with the existing strict document codec, rather than a second permissive object parser.
    private fun objectsDoc(objects:List<CanvasObject>)=CanvasDocument(id="00000000-0000-0000-0000-000000000000",title="wire",width=4096,height=4096,objects=objects)
    private fun delta(d:CanvasDelta)=buildJsonObject {
        put("adds",CanvasCodec.encode(objectsDoc(d.adds)));put("removes",buildJsonArray{d.removes.forEach{add(it)}})
    }
    private fun parseDelta(o:JsonObject):CanvasDelta {
        val adds=CanvasCodec.decode(o.getValue("adds").jsonPrimitive.content).objects
        val removes=o.getValue("removes").jsonArray.also{require(it.size<=CanvasLimits.MAX_OBJECTS)}.map{it.jsonPrimitive.content}
        require(removes.distinct().size==removes.size&&removes.all(CanvasLimits::validId))
        return CanvasDelta(adds,removes)
    }
    fun encode(message:CanvasWire):ByteArray {
        val json=buildJsonObject {
            put("protocol",4)
            when(message) {
                is CanvasWire.Snapshot->{put("type","snapshot");put("session",message.session);put("doc",CanvasCodec.encode(message.doc));put("revision",message.revision);put("host",message.hostActor);put("guest",message.guestActor)}
                is CanvasWire.Propose->{put("type","propose");put("session",message.session);val c=message.command;put("id",c.id);put("sequence",c.sequence);put("base",c.baseRevision);put("kind",c.kind.name);put("delta",delta(c.delta))}
                is CanvasWire.Committed->{put("type","commit");put("session",message.session);val c=message.commit;put("id",c.requestId);put("revision",c.revision);put("delta",delta(c.delta));put("hostUndo",c.hostUndo);put("hostRedo",c.hostRedo);put("guestUndo",c.guestUndo);put("guestRedo",c.guestRedo)}
                is CanvasWire.Rejected->{put("type","reject");put("session",message.session);put("id",message.requestId);put("revision",message.revision);put("reason",message.reason.take(200))}
                is CanvasWire.Cursor->{put("type","cursor");put("session",message.session);put("sequence",message.sequence);put("color",message.color);message.point?.let{put("x",it.x);put("y",it.y)}}
            }
        }.toString().toByteArray()
        require(json.size<=MAX_BYTES);return json
    }
    fun decode(bytes:ByteArray):CanvasWire {
        require(bytes.size in 1..MAX_BYTES);val raw=bytes.toString(Charsets.UTF_8);checkDepth(raw)
        val o=Json.parseToJsonElement(raw).jsonObject
        require(o.getValue("protocol").jsonPrimitive.int==4)
        fun text(key:String)=o.getValue(key).jsonPrimitive.content
        fun id(key:String)=text(key).also{require(CanvasLimits.validId(it))}
        fun long(key:String)=o.getValue(key).jsonPrimitive.long.also{require(it>=0&&it<Long.MAX_VALUE)}
        val session=id("session")
        return when(text("type")) {
            "snapshot"->CanvasWire.Snapshot(session,CanvasCodec.decode(text("doc")),long("revision"),id("host"),id("guest"))
            "propose"->CanvasWire.Propose(session,CanvasCommand(id("id"),long("sequence"),long("base"),CanvasCommandKind.valueOf(text("kind")),parseDelta(o.getValue("delta").jsonObject)))
            "commit"->CanvasWire.Committed(session,CanvasCommit(id("id"),long("revision"),parseDelta(o.getValue("delta").jsonObject),o.getValue("hostUndo").jsonPrimitive.boolean,o.getValue("hostRedo").jsonPrimitive.boolean,o.getValue("guestUndo").jsonPrimitive.boolean,o.getValue("guestRedo").jsonPrimitive.boolean))
            "reject"->CanvasWire.Rejected(session,id("id"),long("revision"),text("reason").also{require(it.length<=200)})
            "cursor"->{val point=if("x" in o || "y" in o)CanvasPoint(o.getValue("x").jsonPrimitive.float,o.getValue("y").jsonPrimitive.float).also{require(it.x.isFinite()&&it.y.isFinite()&&it.x in 0f..4096f&&it.y in 0f..4096f)}else null
                CanvasWire.Cursor(session,long("sequence"),point,o.getValue("color").jsonPrimitive.int)}
            else->error("Неизвестный пакет доски")
        }
    }
    private fun checkDepth(raw:String){var depth=0;var quoted=false;var escaped=false
        raw.forEach{c->if(quoted){if(escaped)escaped=false else if(c=='\\')escaped=true else if(c=='"')quoted=false}
            else if(c=='"')quoted=true else if(c=='{'||c=='['){depth++;require(depth<=24)}else if(c=='}'||c==']'){depth--;require(depth>=0)}}
        require(depth==0&&!quoted)
    }
}
