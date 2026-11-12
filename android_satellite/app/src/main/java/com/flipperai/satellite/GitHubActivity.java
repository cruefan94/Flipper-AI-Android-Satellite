package com.flipperai.satellite;

import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

/** Public GitHub read-only status. Private-repo OAuth is intentionally NOT implemented. */
public final class GitHubActivity extends Activity {
  private static final String API="https://api.github.com/repos/";
  private static final ExecutorService WORK=Executors.newSingleThreadExecutor();
  private EditText repository;
  private TextView output;
  @Override public void onCreate(Bundle state){
    super.onCreate(state);
    ScrollView scroller=new ScrollView(this);LinearLayout root=new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);root.setPadding(24,24,24,24);scroller.addView(root);setContentView(scroller);
    TextView title=new TextView(this);title.setText("GITHUB — READ-ONLY");title.setTextSize(22);root.addView(title);
    TextView privacy=new TextView(this);privacy.setText("Public repositories only. No GitHub account linked, no credentials collected, and no repository writes. Private repository access requires a future secure OAuth authorization flow.");root.addView(privacy);
    repository=new EditText(this);repository.setSingleLine(true);repository.setText("cruefan94/Flipper-AI-Android-Satellite");root.addView(repository);
    output=new TextView(this);output.setText("Choose an action to check public GitHub data.");output.setTextIsSelectable(true);root.addView(output);
    button(root,"CHECK REPOSITORY",()->request("repo"));
    button(root,"VIEW BRANCHES",()->request("branches"));
    button(root,"VIEW RECENT COMMITS",()->request("commits"));
    button(root,"CHECK ACTIONS BUILDS",()->request("actions"));
    button(root,"VIEW RELEASES",()->request("releases"));
    button(root,"MAIN MOMENTUM REPOSITORY",()->{repository.setText("cruefan94/Flipper-AI-Momentum-");request("repo");});
    button(root,"OPEN GITHUB IN BROWSER",()->{
      String name=repo();if(name!=null)startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/"+name)));
    });
    button(root,"BACK TO SATELLITE",this::finish);
  }
  private void button(LinearLayout root,String text,Runnable action){Button b=new Button(this);b.setText(text);b.setOnClickListener(v->action.run());root.addView(b);}
  private String repo(){
    String s=repository.getText().toString().trim();
    return s.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")?s:null;
  }
  private void request(String action){
    String name=repo();if(name==null){output.setText("Enter a valid owner/repository name.");return;}
    String endpoint=API+name;
    if("branches".equals(action))endpoint+="/branches?per_page=15";
    else if("commits".equals(action))endpoint+="/commits?per_page=12";
    else if("actions".equals(action))endpoint+="/actions/runs?per_page=10";
    else if("releases".equals(action))endpoint+="/releases?per_page=10";
    output.setText("Loading "+action+" (public data only)...");
    final String url=endpoint;
    WORK.execute(()->{
      String result;
      HttpURLConnection connection=null;
      try{
        connection=(HttpURLConnection)new URL(url).openConnection();
        connection.setConnectTimeout(8000);connection.setReadTimeout(12000);
        connection.setRequestProperty("Accept","application/vnd.github+json");
        connection.setRequestProperty("User-Agent","FlipperAISatellite-ReadOnly");
        int code=connection.getResponseCode();
        if(code!=200)throw new IOException("GitHub HTTP "+code+" (private repository, rate limit or unavailable)");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(InputStream in=connection.getInputStream()){
          byte[] buffer=new byte[4096];int n;
          while((n=in.read(buffer))!=-1){if(bytes.size()+n>200000)throw new IOException("GitHub response too large");bytes.write(buffer,0,n);}
        }
        String raw=bytes.toString("UTF-8");
        StringBuilder text=new StringBuilder();
        if("repo".equals(action)){
          JSONObject o=new JSONObject(raw);
          text.append(o.optString("full_name")).append("\n").append(o.optString("description")).append("\nDefault branch: ").append(o.optString("default_branch")).append("\nVisibility: ").append(o.optString("visibility"));
        }else{
          JSONArray array="actions".equals(action)?new JSONObject(raw).optJSONArray("workflow_runs"):new JSONArray(raw);
          if(array==null||array.length()==0)text.append("No public results.");
          else for(int i=0;i<Math.min(array.length(),15);i++){
            JSONObject o=array.optJSONObject(i);if(o==null)continue;
            if("branches".equals(action))text.append(o.optString("name"));
            if("commits".equals(action))text.append(o.optString("sha").substring(0,Math.min(9,o.optString("sha").length()))).append(" ").append(o.optJSONObject("commit")==null?"":o.optJSONObject("commit").optJSONObject("message")==null?o.optJSONObject("commit").optString("message"):o.optJSONObject("commit").optString("message"));
            if("actions".equals(action))text.append(o.optString("name")).append(" — ").append(o.optString("status")).append(" / ").append(o.optString("conclusion")).append(" — ").append(o.optString("head_branch"));
            if("releases".equals(action))text.append(o.optString("tag_name")).append(" — ").append(o.optString("name"));
            text.append("\n");
          }
        }
        result=text.toString();
      }catch(Exception e){result="GitHub request failed: "+e.getMessage();}
      finally{if(connection!=null)connection.disconnect();}
      final String value=result;
      runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())output.setText(value);});
    });
  }
}
