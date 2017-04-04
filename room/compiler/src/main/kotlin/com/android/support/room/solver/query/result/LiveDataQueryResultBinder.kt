/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.support.room.solver.query.result

import com.android.support.room.ext.AndroidTypeNames
import com.android.support.room.ext.L
import com.android.support.room.ext.LifecyclesTypeNames
import com.android.support.room.ext.N
import com.android.support.room.ext.RoomTypeNames
import com.android.support.room.ext.RoomTypeNames.INVALIDATION_OBSERVER
import com.android.support.room.ext.T
import com.android.support.room.ext.typeName
import com.android.support.room.solver.CodeGenScope
import com.android.support.room.writer.DaoWriter
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import javax.lang.model.element.Modifier
import javax.lang.model.type.TypeMirror

/**
 * Converts the query into a LiveData and returns it. No query is run until necessary.
 */
class LiveDataQueryResultBinder(val typeArg: TypeMirror, queryTableNames: List<String>,
                                adapter: QueryResultAdapter?)
    : BaseObservableQueryResultBinder(adapter) {
    @Suppress("JoinDeclarationAndAssignment")
    val tableNames = ((adapter?.accessedTableNames() ?: emptyList()) + queryTableNames).toSet()
    override fun convertAndReturn(roomSQLiteQueryVar : String, dbField: FieldSpec,
                                  scope: CodeGenScope) {
        val typeName = typeArg.typeName()

        val liveDataImpl = TypeSpec.anonymousClassBuilder("").apply {
            superclass(ParameterizedTypeName.get(LifecyclesTypeNames.COMPUTABLE_LIVE_DATA,
                    typeName))
            val observerField = FieldSpec.builder(RoomTypeNames.INVALIDATION_OBSERVER,
                    scope.getTmpVar("_observer"), Modifier.PRIVATE).build()
            addField(observerField)
            addMethod(createComputeMethod(
                    observerField = observerField,
                    typeName = typeName,
                    roomSQLiteQueryVar = roomSQLiteQueryVar,
                    dbField = dbField,
                    scope = scope
            ))
            addMethod(createFinalizeMethod(roomSQLiteQueryVar))
        }.build()
        scope.builder().apply {
            addStatement("return $L.getLiveData()", liveDataImpl)
        }
    }

    private fun createComputeMethod(roomSQLiteQueryVar: String, typeName: TypeName,
                                    observerField: FieldSpec, dbField: FieldSpec,
                                    scope: CodeGenScope): MethodSpec {
        return MethodSpec.methodBuilder("compute").apply {
            addAnnotation(Override::class.java)
            addModifiers(Modifier.PROTECTED)
            returns(typeName)

            beginControlFlow("if ($N == null)", observerField).apply {
                addStatement("$N = $L", observerField, createAnonymousObserver())
                addStatement("$N.getInvalidationTracker().addWeakObserver($N)",
                        dbField, observerField)
            }
            endControlFlow()

            createRunQueryAndReturnStatements(this, roomSQLiteQueryVar, scope)
        }.build()
    }

    private fun createAnonymousObserver(): TypeSpec {
        val tableNamesList = tableNames.joinToString(",") { "\"$it\"" }
        return TypeSpec.anonymousClassBuilder(tableNamesList).apply {
            superclass(INVALIDATION_OBSERVER)
            addMethod(MethodSpec.methodBuilder("onInvalidated").apply {
                returns(TypeName.VOID)
                addAnnotation(Override::class.java)
                addModifiers(Modifier.PUBLIC)
                addStatement("invalidate()")
            }.build())
        }.build()
    }
}
