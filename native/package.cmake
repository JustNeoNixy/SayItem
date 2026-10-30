file(MAKE_DIRECTORY "${OUTPUT_DIR}/${PLATFORM}")
get_filename_component(NAME "${INPUT_FILE}" NAME)
file(COPY_FILE "${INPUT_FILE}" "${OUTPUT_DIR}/${PLATFORM}/${NAME}")
file(SHA256 "${INPUT_FILE}" HASH)
file(WRITE "${OUTPUT_DIR}/${PLATFORM}/source.json"
    "{\n  \"jniRevision\":\"1b537f459d299d5c3ce4e12306aceac75009c253\",\n  \"whisperRevision\":\"ebca09a3d1033417b0c630bbbe607b0f185b1488\",\n  \"sha256\":\"${HASH}\",\n  \"cpu\":\"baseline CPU; no AVX2 requirement\",\n  \"patch\":\"CPU scheduler receives per-request abort callback\"\n}\n")
