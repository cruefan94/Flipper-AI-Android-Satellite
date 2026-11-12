package com.flipperai.satellite;

/** Informational independent-model catalog. Does not download unverified weights. */
public final class AiModelCatalog {
  public static final class Model {
    public final String id, family, format, note;
    public final long recommendedFreeBytes;
    Model(String id,String family,String format,long freeBytes,String note){
      this.id=id;this.family=family;this.format=format;this.recommendedFreeBytes=freeBytes;this.note=note;
    }
  }
  private AiModelCatalog(){}
  public static Model[] candidates(){
    return new Model[]{
      new Model("small-instruct-gguf","Qwen2.5 0.5B Instruct","GGUF",2L*1024*1024*1024,"Candidate only: check licensing, provenance and Android runtime compatibility before distribution"),
      new Model("compact-instruct-gguf","Qwen2.5 1.5B Instruct","GGUF",4L*1024*1024*1024,"Candidate only: quantized weights and device memory must be verified")
    };
  }
  public static boolean hasSpace(long usableBytes,Model model){return model!=null&&usableBytes>=model.recommendedFreeBytes;}
}
