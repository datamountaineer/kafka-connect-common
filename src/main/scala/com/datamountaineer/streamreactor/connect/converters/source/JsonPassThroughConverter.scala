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

package com.datamountaineer.streamreactor.connect.converters.source

import java.util.Collections

import com.landoop.json.sql.JacksonJson
import org.apache.kafka.connect.source.SourceRecord


class JsonPassThroughConverter extends Converter {
  override def convert(kafkaTopic: String,
                       sourceTopic: String,
                       messageId: String,
                       bytes: Array[Byte],
                       keys: Seq[String] = Seq.empty,
                       keyDelimiter: String = ".",
                       properties: Map[String, String] = Map.empty): SourceRecord = {
    require(bytes != null, s"Invalid [$bytes] parameter")

    val json = new String(bytes, "utf-8")
    val jsonNode = JacksonJson.asJson(json)
    var keysValue = keys.flatMap { key =>
      Option(KeyExtractor.extract(jsonNode, key.split('.').toVector)).map(_.toString)
    }.mkString(keyDelimiter)

    // If keys are not provided, default one will be constructed
    if (keysValue == "") {
      keysValue = s"$sourceTopic$keyDelimiter$messageId"
    }

    new SourceRecord(Collections.singletonMap(Converter.TopicKey, sourceTopic),
      null,
      kafkaTopic,
      null,
      keysValue,
      null,
      json)
  }
}



