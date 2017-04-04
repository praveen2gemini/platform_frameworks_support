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
import com.android.support.room.ext.N
import com.android.support.room.ext.T
import com.android.support.room.solver.CodeGenScope
import com.android.support.room.writer.DaoWriter
import com.squareup.javapoet.MethodSpec
import javax.lang.model.element.Modifier

/**
 * Base class for query result binders that observe the database. It includes common functionality
 * like creating a finalizer to release the query or creating the actual adapter call code.
 */
abstract class BaseObservableQueryResultBinder(adapter: QueryResultAdapter?)
    : QueryResultBinder(adapter) {

    protected fun createFinalizeMethod(roomSQLiteQueryVar: String): MethodSpec {
        return MethodSpec.methodBuilder("finalize").apply {
            addModifiers(Modifier.PROTECTED)
            addAnnotation(Override::class.java)
            addStatement("$L.release()", roomSQLiteQueryVar)
        }.build()
    }

    protected fun createRunQueryAndReturnStatements(builder: MethodSpec.Builder,
                                                    roomSQLiteQueryVar: String,
                                                    scope: CodeGenScope) {
        val outVar = scope.getTmpVar("_result")
        val cursorVar = scope.getTmpVar("_cursor")
        builder.apply {
            addStatement("final $T $L = $N.query($L)", AndroidTypeNames.CURSOR, cursorVar,
                    DaoWriter.dbField, roomSQLiteQueryVar)
            beginControlFlow("try").apply {
                val adapterScope = scope.fork()
                adapter?.convert(outVar, cursorVar, adapterScope)
                addCode(adapterScope.builder().build())
                addStatement("return $L", outVar)
            }
            nextControlFlow("finally").apply {
                addStatement("$L.close()", cursorVar)
            }
            endControlFlow()
        }
    }
}
