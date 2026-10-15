package app.yodo.messenger.features.canvas

import java.util.UUID

/** No claimed Firebase identity travels over the local link. Actors are allocated by the host. */
data class CanvasDelta(val adds:List<CanvasObject> = emptyList(),val removes:List<String> = emptyList()) {
    fun apply(doc:CanvasDocument):CanvasDocument {
        require(adds.size<=CanvasLimits.MAX_OBJECTS && removes.size<=CanvasLimits.MAX_OBJECTS)
        require(removes.distinct().size==removes.size && removes.all(CanvasLimits::validId))
        require(adds.map{it.id}.distinct().size==adds.size && adds.none{it.id in removes})
        val remaining=doc.objects.filterNot{it.id in removes}
        require(adds.none{item->remaining.any{it.id==item.id}})
        return doc.copy(objects=remaining+adds).also(CanvasLimits::validate)
    }
    companion object {
        fun between(before:CanvasDocument,after:CanvasDocument):CanvasDelta {
            require(before.copy(objects=emptyList())==after.copy(objects=emptyList())) { "В сессии меняются только объекты рисунка" }
            val old=before.objects.associateBy{it.id};val fresh=after.objects.associateBy{it.id}
            require(old.keys.intersect(fresh.keys).all{old[it]==fresh[it]}) { "Изменение существующих объектов не поддерживается" }
            return CanvasDelta(after.objects.filter{it.id !in old},before.objects.filter{it.id !in fresh}.map{it.id})
        }
    }
}
enum class CanvasCommandKind { EDIT, UNDO, REDO }
data class CanvasCommand(val id:String=UUID.randomUUID().toString(),val sequence:Long,val baseRevision:Long,
    val kind:CanvasCommandKind=CanvasCommandKind.EDIT,val delta:CanvasDelta=CanvasDelta())
data class CanvasCommit(val requestId:String,val revision:Long,val delta:CanvasDelta,
    val hostUndo:Boolean,val hostRedo:Boolean,val guestUndo:Boolean,val guestRedo:Boolean)

/** Two-peer authoritative ordering. Reversal touches only object versions still owned by that action. */
class CanvasAuthority(initial:CanvasDocument,val hostActor:String,val guestActor:String) {
    var document=initial;private set
    var revision=0L;private set
    private val versions=initial.objects.associate{it.id to 0L}.toMutableMap()
    private val lastSequence=mutableMapOf(hostActor to 0L,guestActor to 0L)
    private data class Reverse(val before:Map<String,CanvasObject?>,val after:Map<String,CanvasObject?>,val versions:Map<String,Long>)
    private val undo=mutableMapOf(hostActor to ArrayDeque<Reverse>(),guestActor to ArrayDeque<Reverse>())
    private val redo=mutableMapOf(hostActor to ArrayDeque<Reverse>(),guestActor to ArrayDeque<Reverse>())
    init {CanvasLimits.validate(initial);require(CanvasLimits.validId(hostActor)&&CanvasLimits.validId(guestActor)&&hostActor!=guestActor)}
    fun canUndo(actor:String)=undo.getValue(actor).isNotEmpty()
    fun canRedo(actor:String)=redo.getValue(actor).isNotEmpty()
    fun execute(actor:String,command:CanvasCommand):CanvasCommit {
        require(revision<5000) { "Лимит сессии: 5000 действий. Начните новую сессию с сохранённой доской." }
        require(actor in lastSequence && CanvasLimits.validId(command.id))
        require(command.sequence==lastSequence.getValue(actor)+1) { "Повтор или пропуск номера операции" }
        // Consume a syntactically valid request even if its edit is rejected. Caller returns a rejection ACK.
        lastSequence[actor]=command.sequence
        require(command.baseRevision in 0..revision) { "Неизвестная версия доски" }
        val prior=document.objects.associateBy{it.id}
        val delta:CanvasDelta
        if(command.kind==CanvasCommandKind.EDIT) {
            require(command.delta.removes.all{it in prior && versions.getValue(it)<=command.baseRevision}) { "Объект уже изменился. Повторите действие." }
            // IDs are never reused by a new edit, including tombstoned IDs.
            require(versions.size+command.delta.adds.size<=10000) { "Лимит объектов за сессию. Начните новую сессию." }
            require(command.delta.adds.none{it.id in versions}) { "Идентификатор объекта уже использован" }
            delta=command.delta
            val next=delta.apply(document)
            val ids=(delta.adds.map{it.id}+delta.removes).toSet()
            if(ids.isNotEmpty()) {
                val after=next.objects.associateBy{it.id}
                push(undo.getValue(actor),Reverse(ids.associateWith{prior[it]},ids.associateWith{after[it]},ids.associateWith{revision+1}))
                redo.getValue(actor).clear()
            }
        } else {
            require(command.delta==CanvasDelta())
            val stack=if(command.kind==CanvasCommandKind.UNDO)undo.getValue(actor) else redo.getValue(actor)
            val other=if(command.kind==CanvasCommandKind.UNDO)redo.getValue(actor) else undo.getValue(actor)
            val record=stack.lastOrNull()
            val ids=record?.after?.keys?.filter{prior[it]==record.after[it]&&versions[it]==record.versions[it]}?:emptyList()
            val adds=ids.mapNotNull{record!!.before[it]};val removes=ids.filter{prior[it]!=null}
            // Object values are immutable, so inverse operations never replace a currently present ID.
            delta=CanvasDelta(adds=adds,removes=removes)
            delta.apply(document) // validate before consuming undo stack (e.g. board budget)
            if(record!=null)stack.removeLast()
            if(ids.isNotEmpty())push(other,Reverse(ids.associateWith{record!!.after[it]},ids.associateWith{record!!.before[it]},ids.associateWith{revision+1}))
        }
        val next=delta.apply(document)
        revision++;document=next
        (delta.adds.map{it.id}+delta.removes).forEach{versions[it]=revision}
        return CanvasCommit(command.id,revision,delta,canUndo(hostActor),canRedo(hostActor),canUndo(guestActor),canRedo(guestActor))
    }
    private fun push(stack:ArrayDeque<Reverse>,record:Reverse){stack.addLast(record);while(stack.size>60)stack.removeFirst()}
}

data class CanvasRemoteCursor(val point:CanvasPoint,val color:Int,val seenAt:Long)
