# JSON Codec v0.7.0

This microservice can encode and decode JSON messages received via HTTP or any other transport

## Configuration

Main configuration is done via setting following properties in `codecSettings` block of a custom configuration:

+ **messageTypeDetection** - determines how the codec will detect a type of incoming message  
  Can be one of the following:
    + `BY_HTTP_METHOD_AND_URI` - message type will be detected based on the values of `method` and `uri` message metadata properties (default)
    + `BY_INNER_FIELD` - message type will be retrieved from a message field specified by `messageTypeField` setting
    + `CONSTANT` - message type for decode messages will be always the same, message type will be taken from `constantMessageType` option
+ **messageTypeField** - a JSON pointer to the field containing message type (used only if `messageTypeDetection` = `BY_INNER_FIELD`).
+ **constantMessageType** - a constant message type for decode, if CONSTANT option is turned on
  More information about JSON pointer can be found [here](https://datatracker.ietf.org/doc/html/draft-ietf-appsawg-json-pointer-03#section-2).
  **Examples:**
  ```json
  {
    "simple": 42,
    "object": {
      "simple": 54
    },
    "simple_collection": [89, 5],
    "object_collection": [
      {
        "simple": "test"
      }
    ]
  }
  ```
  |JSON pointer|Result|
  |:---|:---|
  |/simple|42|
  |/object/simple|54|
  |/simple_collection/1|5|
  |/object_collection/0/simple|test|
+ **rejectUnexpectedFields** - messages with unknown fields will be rejected during decoding (`true` by default)
+ **treatSimpleValuesAsStrings** - allows decoding of primitive values from JSON string e.g. `"1"` can be decoded as number, `"true"` as boolean, etc (`false` by default)

### Configuration example

```yaml
messageTypeDetection: BY_INNER_FIELD
messageTypeField: "messageType"
rejectUnexpectedFields: true
treatSimpleValuesAsStrings: false
```

```yaml
messageTypeDetection: CONSTANT
constantMessageType: "TypeFromDictionary"
```

## Encoding

Codec will attempt to encode all parsed messages in a message group if their protocol is set to `json`  
Messages will be encoded into raw messages containing byte-string with JSON.  
Raw message metadata will contain `uri` and `method` properties if it was decoded from HTTP request

### Decoding

Codec will attempt to decode all raw messages in a message group as JSON.  
If `messageTypeDetection` is set to `BY_HTTP_METHOD_AND_URI` these messages are required to have `uri` and `method` metadata properties.

## Deployment via `infra-mgr`

Here's an example of `infra-mgr` config required to deploy this service

```yaml
apiVersion: th2.exactpro.com/v1
kind: Th2Box
metadata:
  name: codec-json
spec:
  image-name: ghcr.io/th2-net/th2-codec-json
  image-version: 0.4.1
  custom-config:
    codecSettings:
      messageTypeDetection: BY_INNER_FIELD
      messageTypeField: "messageType"
      rejectUnexpectedFields: true
      treatSimpleValuesAsStrings: false
  type: th2-codec
  pins:
    # encoder
    - name: in_codec_encode
      connection-type: mq
      attributes:
        - encoder_in
        - subscribe
        - group
    - name: out_codec_encode
      connection-type: mq
      attributes:
        - encoder_out
        - publish
        - group
    # decoder
    - name: in_codec_decode
      connection-type: mq
      attributes:
        - decoder_in
        - subscribe
        - group
    - name: out_codec_decode
      connection-type: mq
      attributes:
        - decoder_out
        - publish
        - group
    # encoder general (technical)
    - name: in_codec_general_encode
      connection-type: mq
      attributes:
        - general_encoder_in
        - subscribe
        - group
    - name: out_codec_general_encode
      connection-type: mq
      attributes:
        - general_encoder_out
        - publish
        - group
    # decoder general (technical)
    - name: in_codec_general_decode
      connection-type: mq
      attributes:
        - general_decoder_in
        - subscribe
        - group
    - name: out_codec_general_decode
      connection-type: mq
      attributes:
        - general_decoder_out
        - publish
        - group
  extended-settings:
    service:
      enabled: false
```

## Changelog

### v0.7.0

#### Changed:

* gRPC interface for codec pipeline

### v0.6.0

#### Changed:

* Support for parameters substitution in URI.
* Codec core version updated from `4.3.0` to `4.7.0`

### v0.5.1

#### Changed:

* rejection error message
* bump `com.exactpro.th2:common` dependency to `3.32.0`
* bump `com.exactpro.th2:bom` dependency to `3.1.0`
* bump `com.exactpro.th2:codec` dependency to `4.3.0`

### v0.5.0

#### Changed:

* bump `com.exactpro.th2:common` dependency to `3.31.1`

### v0.4.1

#### Added:

* ability to decode messages with constant type

#### Changed:

* bump `com.exactpro.th2:common` dependency to `3.31.0`

### v0.4.0

#### Added:

* ability to decode/encode simple values from JSON root

#### Changed:

* update `th2-codec` version from 4.0.0 to 4.1.1
* update `sailfish` dependencies from 3.2.1622 to 3.2.1736
* update `common` version from 3.17.0 to 3.29.1

#### Fixed:

* The filter for pins by `message_type` is not working

### v0.3.0

#### Changed:

* migrated to the `th2-codec` version 4.0.0

### v0.2.0

#### Added:

* ability to use a JSON pointer to specify the path to the field with message type

### v0.1.3

#### Changed:

* parent event id loss fixed

### v0.1.2

#### Changed:

* use newer version of `com.exactpro.th2:sailfish-utils` dependency

### v0.1.1

#### Changed:

* use newer version of SF JSON codec which properly encodes decimals to strings
* use newer versions of th2 libraries

### v0.1.0

#### Added:

* ability to determine message type from message field