# JSON Codec v0.1.3

This microservice can encode and decode JSON messages received via HTTP or any other transport

## Configuration

Main configuration is done via setting following properties in `codecSettings` block of a custom configuration:

+ **messageTypeDetection** - determines how the codec will detect a type of incoming message  
  Can be one of the following:
    + `BY_HTTP_METHOD_AND_URI` - message type will be detected based on the values of `method` and `uri` message metadata properties (default)
    + `BY_INNER_FIELD` - message type will be retrieved from a message field specified by `messageTypeField` setting
+ **messageTypeField** - name of a field containing message type (used only if `messageTypeDetection` = `BY_INNER_FIELD`)
+ **rejectUnexpectedFields** - messages with unknown fields will be rejected during decoding (`true` by default)
+ **treatSimpleValuesAsStrings** - allows decoding of primitive values from JSON string e.g. `"1"` can be decoded as number, `"true"` as boolean, etc (`false` by default)

### Configuration example

```yaml
messageTypeDetection: BY_INNER_FIELD
messageTypeField: "messageType"
rejectUnexpectedFields: true
treatSimpleValuesAsStrings: false
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
  image-version: 0.1.2
  custom-config:
    codecSettings:
      messageTypeDetection: BY_INNER_FIELD
      messageTypeField: "messageType"
      rejectUnexpectedFields: true
      treatSimpleValuesAsStrings: false
  type: th2-conn
  pins:
    # encoder
    - name: in_codec_encode
      connection-type: mq
      attributes:
        - encoder_in
        - subscribe
    - name: out_codec_encode
      connection-type: mq
      attributes:
        - encoder_out
        - publish
    # decoder
    - name: in_codec_decode
      connection-type: mq
      attributes:
        - decoder_in
        - subscribe
    - name: out_codec_decode
      connection-type: mq
      attributes:
        - decoder_out
        - publish
    # encoder general (technical)
    - name: in_codec_general_encode
      connection-type: mq
      attributes:
        - general_encoder_in
        - subscribe
    - name: out_codec_general_encode
      connection-type: mq
      attributes:
        - general_encoder_out
        - publish
    # decoder general (technical)
    - name: in_codec_general_decode
      connection-type: mq
      attributes:
        - general_decoder_in
        - subscribe
    - name: out_codec_general_decode
      connection-type: mq
      attributes:
        - general_decoder_out
        - publish
  extended-settings:
    service:
      enabled: false
```

## Changelog

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