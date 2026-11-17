package com.flipperai.satellite;

/**
 * Curated, opt-in LiteRT-LM downloads. Each artifact is pinned to an immutable
 * upstream revision and exact size/SHA-256. This is not an arbitrary URL installer.
 * The 614 MB Qwen3 INT8 export is compatible with LiteRT-LM 0.18.0.
 */
public final class VettedModelCatalog {
  private VettedModelCatalog() {}

  public static final class Entry {
    public final String id, displayName, fileName, url, sha256, license;
    public final long bytes;
    private Entry(String id,String displayName,String fileName,String url,
        long bytes,String sha256,String license) {
      this.id=id;this.displayName=displayName;this.fileName=fileName;
      this.url=url;this.bytes=bytes;this.sha256=sha256;this.license=license;
    }
  }

  public static final Entry QWEN3_0_6B=new Entry(
    "qwen3-0.6b-litert-int8",
    "Qwen3-0.6B (LiteRT INT8)",
    "Qwen3-0.6B.litertlm",
    "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/"
      +"e6687177e00897731e7716267eebd4eba0dde7c2/Qwen3-0.6B.litertlm",
    613722264L,
    "c5f39b4191926786c3e6668b62af4d6eb15248dfc7a5348252a9b406f33f2108",
    "Apache-2.0"
  );
}
