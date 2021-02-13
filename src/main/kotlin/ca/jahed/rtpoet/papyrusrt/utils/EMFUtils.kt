package ca.jahed.rtpoet.papyrusrt.utils

import org.eclipse.emf.ecore.EClassifier
import org.eclipse.emf.ecore.EObject

object EMFUtils {
    fun getObjectByType(
        objects: List<EObject>,
        type: EClassifier,
    ): EObject? {
        return getObjectByType(mapOf(Pair(type, objects)), listOf(type))
    }

    fun getObjectByType(
        objects: List<EObject>,
        type: EClassifier,
        attrs: Map<String, Any>,
    ): EObject? {
        return getObjectByType(mapOf(Pair(type, objects)), listOf(type), attrs)
    }

    fun getObjectByType(
        objects: Map<EClassifier, List<EObject>>,
        type: EClassifier,
        attrs: Map<String, Any> = emptyMap(),
    ): EObject? {
        return getObjectByType(objects, listOf(type), attrs)
    }

    fun getObjectByType(
        objects: Map<EClassifier, List<EObject>>,
        types: List<EClassifier>,
        attrs: Map<String, Any> = emptyMap(),
    ): EObject? {
        val eObjs = getObjectsByType(objects, types, attrs)
        return if (eObjs.isNotEmpty()) eObjs[0] else null
    }

    fun getObjectsByType(
        objects: Map<EClassifier, List<EObject>>,
        types: List<EClassifier>,
    ): List<EObject> {
        return getObjectsByType(objects, types, emptyMap())
    }

    fun getObjectsByType(
        objects: Map<EClassifier, List<EObject>>, types: List<EClassifier>,
        attrs: Map<String, Any> = emptyMap(),
    ): List<EObject> {
        val candidates = getCandidates(objects, types)
        val found = mutableListOf<EObject>()

        candidates.forEach { eObj ->
            var matchedAttrs = 0
            eObj.eClass().eAllAttributes.filter { attrs.containsKey(it.name) }.forEach loop@{ eAttr ->
                if (attrs[eAttr.name] != eObj.eGet(eAttr)) return@loop
                matchedAttrs++
            }
            if (matchedAttrs == attrs.size) found.add(eObj)
        }

        return found
    }

    fun getReferencingObjectByType(
        objects: List<EObject>,
        type: EClassifier, target: EObject,
    ): EObject? {
        return getReferencingObjectByType(mapOf(Pair(type, objects)), type, target)
    }

    fun getReferencingObjectByType(
        objects: Map<EClassifier, List<EObject>>,
        type: EClassifier, target: EObject,
    ): EObject? {
        return getReferencingObjectByType(objects, listOf(type), target)
    }

    fun getReferencingObjectByType(
        objects: Map<EClassifier, List<EObject>>,
        types: List<EClassifier>, target: EObject,
    ): EObject? {
        val eObjs = getReferencingObjectsByType(objects, types, target)
        return if (eObjs.isNotEmpty()) eObjs[0] else null
    }

    fun getReferencingObjectsByType(
        objects: Map<EClassifier, List<EObject>>,
        types: List<EClassifier>, target: EObject,
    ): List<EObject> {
        val candidates = getCandidates(objects, types)
        val found = mutableListOf<EObject>()
        candidates.forEach { eObj ->
            eObj.eClass().eAllReferences.forEach loop@{ eRef ->
                if (target == eObj.eGet(eRef)) {
                    found.add(eObj)
                    return@loop
                }
            }
        }
        return found
    }

    private fun getCandidates(
        objects: Map<EClassifier, List<EObject>>,
        types: List<EClassifier>,
    ): Set<EObject> {
        val candidates = mutableSetOf<EObject>()
        types.forEach { classifier ->
            objects[classifier]?.filter { it.eClass() == classifier }?.forEach { candidates.add(it) }
        }
        return candidates
    }
}
