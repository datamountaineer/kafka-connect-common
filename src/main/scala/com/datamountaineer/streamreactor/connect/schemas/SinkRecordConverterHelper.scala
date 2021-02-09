package com.datamountaineer.streamreactor.connect.schemas

import com.datamountaineer.streamreactor.connect.converters.source.JsonSimpleConverter
import com.datamountaineer.streamreactor.connect.schemas.StructHelper.StructExtension
import com.fasterxml.jackson.databind.ObjectMapper
import com.typesafe.scalalogging.StrictLogging
import org.apache.kafka.connect.data.{Schema, SchemaBuilder, Struct}
import org.apache.kafka.connect.errors.ConnectException
import org.apache.kafka.connect.header.ConnectHeaders
import org.apache.kafka.connect.sink.SinkRecord

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

object SinkRecordConverterHelper extends StrictLogging {

  implicit final class SinkRecordExtension(val record: SinkRecord)
      extends AnyVal {

    /**
      * make new sink record, taking fields
      * from the key, value and headers
      * */
    def newFilteredRecord(
        fields: Map[String, String],
        ignoreFields: Set[String] = Set.empty[String],
        keyFields: Map[String, String] = Map.empty[String, String],
        headerFields: Map[String, String] = Map.empty[String, String],
        retainKey: Boolean = false,
        retainHeaders: Boolean = false): SinkRecord = {

      //if we have keys fields and a key value extract
      val keyStruct = if (keyFields.nonEmpty && record.key() != null) {
        extract(payload = record.key(),
                payloadSchema = record.keySchema(),
                fields = keyFields,
                ignoreFields = Set.empty)
      } else {
        logger.debug(
          s"Key is null for topic [${record.topic()}], partition [${record
            .kafkaPartition()}], offset [${record.kafkaOffset()}])")
        new Struct(SchemaBuilder.struct().build())
      }

      //if we have value fields and a value extract
      val valueStruct = if (fields.nonEmpty && record.value() != null) {
        extract(payload = record.value(),
                payloadSchema = record.valueSchema(),
                fields = fields,
                ignoreFields = ignoreFields)
      } else {
        logger.debug(
          s"Value is null for topic [${record.topic()}], partition [${record
            .kafkaPartition()}], offset [${record.kafkaOffset()}])")
        new Struct(SchemaBuilder.struct().build())
      }

      //if we have headers fields and values extract
      val headerStruct =
        if (headerFields.nonEmpty && !record.headers().isEmpty) {
          val headerAsSinkRecord = headerToSinkRecord(record)
          extract(payload = headerAsSinkRecord.value(),
                  payloadSchema = headerAsSinkRecord.valueSchema(),
                  fields = headerFields,
                  ignoreFields = Set.empty)
        } else {
          logger.debug(
            s"Headers are empty for topic [${record.topic()}], partition [${record
              .kafkaPartition()}], offset [${record.kafkaOffset()}])")
          new Struct(SchemaBuilder.struct().build())
        }

      //create a new struct with the keys, values and headers
      val struct = keyStruct ++ valueStruct ++ headerStruct

      new SinkRecord(
        record.topic(),
        record.kafkaPartition(),
        if (retainKey) record.keySchema() else null,
        if (retainKey) record.key() else null,
        struct.schema(),
        struct,
        record.kafkaOffset(),
        record.timestamp(),
        record.timestampType(),
        if (retainHeaders) record.headers() else new ConnectHeaders()
      )
    }

    //convert headers to sink record
    def headerToSinkRecord(record: SinkRecord): SinkRecord = {
      val schemaBuilder = SchemaBuilder.struct()
      val asScala = record.headers().asScala
      asScala
        .filterNot(h => h.schema() == null)
        .foreach(h => schemaBuilder.field(h.key(), h.schema()))

      val schema = schemaBuilder.build()
      val newStruct = new Struct(schema)

      asScala
        .filterNot(h => h.schema() == null)
        .foreach(h => newStruct.put(h.key(), h.value()))

      new SinkRecord("header", 0, null, null, newStruct.schema(), newStruct, 0)
    }

    // create a new struct with the required fields
    private def extract(payload: Object,
                        payloadSchema: Schema,
                        fields: Map[String, String],
                        ignoreFields: Set[String]): Struct = {

      if (payloadSchema == null) {

        val struct = toStructFromJson(payload)
        // converted so now reduce the schema
        struct.reduceSchema(schema = struct.schema(),
                            fields = fields,
                            ignoreFields = ignoreFields)

      } else {
        payloadSchema.`type`() match {
          //struct
          case Schema.Type.STRUCT =>
            payload
              .asInstanceOf[Struct]
              .reduceSchema(schema = payloadSchema,
                            fields = fields,
                            ignoreFields = ignoreFields)

          // json with string schema
          case Schema.Type.STRING =>
            val struct = toStructFromStringAndJson(payload, payloadSchema, "")
            struct.reduceSchema(schema = struct.schema(),
                                fields = fields,
                                ignoreFields)

          case other =>
            throw new ConnectException(
              s"[$other] schema is not supported for extracting fields for topic [${record
                .topic()}], partition [${record
                .kafkaPartition()}], offset [${record.kafkaOffset()}]")
        }
      }
    }

    //handle json no schema
    private def toStructFromJson(payload: Object): Struct = {
      Try(payload.asInstanceOf[java.util.HashMap[String, Any]]) match {
        case Success(map) =>
          convert(new ObjectMapper().writeValueAsString(payload))

        case Failure(_) =>
          throw new ConnectException(s"[${payload.getClass}] is not valid. Expecting a Map[String, Any] for topic [${record
            .topic()}], partition [${record.kafkaPartition()}], offset [${record.kafkaOffset()}]")
      }
    }

    //handle json with string schema
    private def toStructFromStringAndJson(payload: Object,
                                          payloadSchema: Schema,
                                          name: String): Struct = {

      val expectedInput = payloadSchema != null && payloadSchema
        .`type`() == Schema.STRING_SCHEMA.`type`()
      if (!expectedInput) {
        throw new ConnectException(
          s"[$payload] is not handled. Expecting Schema.String")
      } else {
        payload match {
          case s: String => convert(s)
          case other =>
            throw new ConnectException(
              s"[${other.getClass}] is not valid. Expecting a Struct")
        }
      }
    }

    private def convert(json: String): Struct = {
      val schemaAndValue = JsonSimpleConverter.convert("", json)
      schemaAndValue.schema().`type`() match {
        case Schema.Type.STRUCT =>
          schemaAndValue.value().asInstanceOf[Struct]

        case other =>
          throw new ConnectException(
            s"[$other] schema is not supported for extracting fields for topic [${record
              .topic()}], partition [${record
              .kafkaPartition()}], offset [${record.kafkaOffset()}]")
      }
    }
  }
}
