package app.yodo.messenger.features.canvas

import kotlinx.serialization.json.*

object CanvasCodec {
    fun encode(doc: CanvasDocument): String {
        CanvasLimits.validate(doc)
        return buildJsonObject {
            put("format", "yodo.canvas"); put("version", if(doc.objects.any{it.kind in CanvasStage4Kinds})3 else if(doc.objects.any{it.kind==CanvasObjectKind.FORMULA})2 else 1); put("id", doc.id); put("title", doc.title)
            put("width", doc.width); put("height", doc.height); put("grid", doc.grid.name); put("template", doc.template.name)
            doc.backgroundBase64?.let { put("background", it) }
            put("objects", buildJsonArray { doc.objects.forEach { obj -> add(buildJsonObject {
                put("id", obj.id); put("kind", obj.kind.name); put("color", obj.color); put("width", obj.width)
                put("text", obj.text); put("fontSize", obj.fontSize)
                put("points", buildJsonArray { obj.points.forEach { point -> add(buildJsonArray { add(point.x); add(point.y) }) } })
            }) } })
        }.toString()
    }
    fun decode(raw: String): CanvasDocument {
        require(raw.toByteArray(Charsets.UTF_8).size <= CanvasLimits.MAX_JSON_BYTES)
        requireShallowCanvasJson(raw)
        val json = Json.parseToJsonElement(raw).jsonObject
        val version=json["version"]?.jsonPrimitive?.int
        require(json["format"]?.jsonPrimitive?.content == "yodo.canvas" && version in listOf(1,2,3))
        val rows = json.getValue("objects").jsonArray
        require(rows.size <= CanvasLimits.MAX_OBJECTS)
        var total = 0
        val objects = rows.map { row ->
            val obj = row.jsonObject; val points = obj.getValue("points").jsonArray
            total += points.size; require(points.size <= CanvasLimits.MAX_POINTS_PER_OBJECT && total <= CanvasLimits.MAX_TOTAL_POINTS)
            CanvasObject(obj.getValue("id").jsonPrimitive.content, CanvasObjectKind.valueOf(obj.getValue("kind").jsonPrimitive.content),
                points.map { pair -> val values = pair.jsonArray; require(values.size == 2); CanvasPoint(values[0].jsonPrimitive.float, values[1].jsonPrimitive.float) },
                obj.getValue("color").jsonPrimitive.int, obj.getValue("width").jsonPrimitive.float,
                obj["text"]?.jsonPrimitive?.content ?: "", obj["fontSize"]?.jsonPrimitive?.float ?: 32f)
        }
        require(version!=1||objects.none{it.kind==CanvasObjectKind.FORMULA}){"Для формул нужен формат документа v2"}
        require(version==3||objects.none{it.kind in CanvasStage4Kinds}){"Для новых фигур нужен формат документа v3"}
        return CanvasDocument(json.getValue("id").jsonPrimitive.content, json.getValue("title").jsonPrimitive.content,
            json.getValue("width").jsonPrimitive.int, json.getValue("height").jsonPrimitive.int,
            CanvasGrid.valueOf(json.getValue("grid").jsonPrimitive.content), CanvasTemplate.valueOf(json.getValue("template").jsonPrimitive.content),
            objects, json["background"]?.jsonPrimitive?.content).also { CanvasLimits.validate(it) }
    }
}

/** Apply before every remote JSON parse, including JSON documents embedded as strings. */
internal fun requireShallowCanvasJson(raw:String){var depth=0;var quoted=false;var escaped=false
    raw.forEach{c->if(quoted){if(escaped)escaped=false else if(c=='\\')escaped=true else if(c=='"')quoted=false}
        else if(c=='"')quoted=true else if(c=='{'||c=='['){depth++;require(depth<=24)}else if(c=='}'||c==']'){depth--;require(depth>=0)}}
    require(depth==0&&!quoted)
}
