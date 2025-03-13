package com.dshatz.openapi2ktor.kdoc

import com.dshatz.openapi2ktor.generators.Type
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock

data class DocTemplate(val template: String, val typeParams: List<Type>, val docs: List<DocTemplate> = emptyList()) {
    companion object {
        fun of(template: String?) = template?.let { DocTemplate(template, emptyList(), emptyList()) }
    }
    class Builder() {
        var template = ""
        val types = mutableListOf<Type>()
        var docs = mutableListOf<DocTemplate>()
        fun addTypeLink(type: Type): Builder {
            template += "%typelink${types.size}:L"
            types += type
            return this
        }
        fun addDoc(docTemplate: DocTemplate): Builder {
            template += "%doc${docs.size}:L"
            docs += docTemplate
            return this
        }
        fun add(literal: String?): Builder {
            if (literal != null) {
                template += literal
            }
            return this
        }
        fun newLine(): Builder {
            template += "\n"
            return this
        }

        fun <T> addMany(many: Collection<T>, block: Builder.(Int, T) -> Unit): Builder {
            many.forEachIndexed {  idx, t ->
                block(this, idx, t)
            }
            return this
        }

        fun <T> addOptional(t: T?, block: Builder.(t: T) -> Unit): Builder {
            if (t != null) block(this, t)
            return this
        }

        fun addDocFor(type: Type?): Builder {
            if (type != null) {
                template += "%docfortype${types.size}:L"
                types += type
            }
            return this
        }

        fun build(): DocTemplate = DocTemplate(template, types, docs)
    }

    fun toCodeBlock(resolveType: (Type) -> Type.WithTypeName?): CodeBlock {
        var finalTemplate = template
        val codeBlockFormatParams = mutableMapOf<String, Any>()
        typeParams.forEachIndexed { idx, type ->
            val typeName = type as? Type.WithTypeName ?: resolveType(type)
            if (typeName != null) {
                if (finalTemplate.contains("%typelink$idx:L")) {
                    finalTemplate = finalTemplate.replace("%typelink$idx:L", "[%type${idx}name:L][%type${idx}:L]")
                    codeBlockFormatParams["type${idx}name"] = (typeName.typeName as ClassName).simpleName
                    codeBlockFormatParams["type${idx}"] = typeName.qualifiedName()
                }
                else if (finalTemplate.contains("%docfortype$idx:L")) {
                    typeName.description?.let { desc ->
                        codeBlockFormatParams["docfortype$idx"] = desc.toCodeBlock(resolveType)
                    }
                    if (typeName.description == null) {
                        finalTemplate = finalTemplate.replace("%docfortype$idx:L", "")
                    }
                }
            }
        }
        docs.forEachIndexed { idx, doc ->
            codeBlockFormatParams["doc$idx"] = doc.toCodeBlock(resolveType)
        }
        return CodeBlock.builder().addNamed(finalTemplate.escapePercents().escapeSlashStar(), codeBlockFormatParams).build()
    }

    private fun String.escapePercents(): String {
        return replace(Regex("%(?![a-zA-Z0-9]+:L)"), "%%")
    }

    private fun String.escapeSlashStar(): String {
        return replace(Regex("/\\*"), "`*`")
    }
}