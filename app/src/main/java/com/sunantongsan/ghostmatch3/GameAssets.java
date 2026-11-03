package com.sunantongsan.ghostmatch3;
import android.content.Context;
import android.graphics.*;
import java.io.*;

/** Prepared off the UI thread and published only after every bitmap is ready. */
final class GameAssets {
    final StoryArt story;
    final Bitmap scenery,ghosts;
    GameAssets(Context context)throws Exception{
        story=new StoryArt(context);
        try(InputStream in=context.getAssets().open("ser-joy-neighborhood.webp")){
            scenery=BitmapFactory.decodeStream(in);
        }
        ghosts=BitmapFactory.decodeResource(context.getResources(),R.drawable.ghost_sprites);
        if(scenery==null||ghosts==null)throw new IOException("Missing game artwork");
    }
}
