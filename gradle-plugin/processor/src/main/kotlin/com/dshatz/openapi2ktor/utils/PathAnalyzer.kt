package com.dshatz.openapi2ktor.utils

import com.reprezen.kaizen.oasparser.model3.Path
import kotlinx.serialization.builtins.MapEntrySerializer

fun analyzePaths(paths: Map<String, Path>) {
    val tree = buildPathTree(paths)
}

fun findLeafPaths(node: TreeNode, currentPath: String = "", leafPaths: MutableList<String> = mutableListOf()): List<String> {
    val newPath = if (currentPath.isNotEmpty()) "$currentPath/${node.path.substringAfterLast('/')}" else node.path.substringAfterLast('/')

    if (node.children.isEmpty()) {
        leafPaths.add(newPath)
    } else {
        for (child in node.children) {
            findLeafPaths(child, newPath, leafPaths)
        }
    }
    return leafPaths
}

private fun <K, V> List<Map.Entry<K, V>>.toMap(): Map<K, V> {
    return this.associate { it.toPair() }
}

fun TreeNode.getAllPaths(): Map<String, Path> {
    val recursive = children.flatMap { it.getAllPaths().entries }
    return if (pathObj != null) {
        (recursive + mapOf(path to pathObj).entries.first()).toMap()
    } else {
        recursive.toMap()
    }
}

fun findLongestCommonPrefix(paths: List<String>): String {
    if (paths.isEmpty()) return ""
    if (paths.size == 1) return paths[0]

    val firstPathParts = paths[0].split('/')
    var commonPrefix = ""

    for (i in firstPathParts.indices) {
        val currentPart = firstPathParts[i]
        if (paths.all { it.split('/').getOrNull(i) == currentPart }) {
            commonPrefix += if (commonPrefix.isEmpty()) currentPart else "/$currentPart"
        } else {
            break
        }
    }
    return commonPrefix
}


data class TreeNode(val path: String, val pathObj: Path? = null, val children: MutableList<TreeNode> = mutableListOf()) {
    override fun toString(): String {
        return path.substringAfterLast('/')
    }
}

internal fun buildPathTree(paths: Map<String, Path>): TreeNode {
    val root = TreeNode("", null, mutableListOf()) // Root node with empty path

    val nodes = mutableMapOf<String, TreeNode>()
    nodes[""] = root

    for (path in paths.values.sortedBy { it.pathString }) {
        var currentPathString = path.pathString
        val pathParts = mutableListOf<String>()

        while (currentPathString.isNotEmpty() && !nodes.containsKey(currentPathString)) {
            pathParts.add(currentPathString)
            val lastSlashIndex = currentPathString.lastIndexOf('/')
            if (lastSlashIndex != -1) {
                currentPathString = currentPathString.substring(0, lastSlashIndex)
            } else {
                currentPathString = "" // Reached root
            }
        }

        pathParts.reversed().forEach { part ->
            nodes.getOrPut(part) { TreeNode(part, pathObj = paths[part]) }
            val lastSlashIndex = part.lastIndexOf('/')
            val parentPathString = if (lastSlashIndex != -1) part.substring(0, lastSlashIndex) else ""
            val parentNode = nodes[parentPathString]!!
            val currentNode = nodes[part]!!
            parentNode.children.add(currentNode)
        }
    }

    return root
}

