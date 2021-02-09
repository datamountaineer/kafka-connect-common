/*
 *  Copyright 2017 Datamountaineer.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.datamountaineer.streamreactor.connect.converters.sink

import com.datamountaineer.streamreactor.connect.schemas.ConverterUtil
import com.fasterxml.jackson.databind.ObjectMapper
import com.landoop.json.sql.JacksonJson
import org.apache.kafka.connect.data.Schema
import org.apache.kafka.connect.errors.ConnectException
import org.apache.kafka.connect.sink.SinkRecord
import org.json4s.jackson.JsonMethods._

import scala.util.Try

/**
  * Created by andrew@datamountaineer.com on 29/12/2016. 
  * kafka-connect-common
  */
object SinkRecordToJson extends ConverterUtil {

  private val mapper = new ObjectMapper()

  def apply(record: SinkRecord): String = {

    val schema = record.valueSchema()
    val value = record.value()

    if (schema == null) {
      if(value == null){
        throw new IllegalArgumentException(s"The sink record value is null.(topic=${record.topic()} partition=${record.kafkaPartition()} offset=${record.kafkaOffset()})".stripMargin)
      }
      //try to take it as string
      value match {
        case map: java.util.Map[_, _] =>
          mapper.writeValueAsString(map)

        case other => sys.error(
          s"""
             |For schemaless record only String and Map types are supported. Class =${Option(other).map(_.getClass.getCanonicalName).getOrElse("unknown(null value)}")}
             |Record info:
             |topic=${record.topic()} partition=${record.kafkaPartition()} offset=${record.kafkaOffset()}
             |${Try(JacksonJson.toJson(value)).getOrElse("")}""".stripMargin)
      }
    } else {
      schema.`type`() match {
        case Schema.Type.STRING =>
          mapper.writeValueAsString(value)

        case Schema.Type.STRUCT =>


          simpleJsonConverter.fromConnectData(record.valueSchema(), record.value()).toString

        case other => sys.error(s"$other schema is not supported")
      }
    }
  }
}

