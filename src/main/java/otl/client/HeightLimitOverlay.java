package otl.client;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/** Darkens blocks that appear after their chunk's initial snapshot at the map build limit. */
final class HeightLimitOverlay {
    static final HeightLimitOverlay INSTANCE=new HeightLimitOverlay();
    static final int INDICATOR_WIDTH=56,INDICATOR_HEIGHT=26;
    private volatile MapInfo map;
    private String currentName;
    private volatile int request;
    private int layer=-1;
    private World scannedWorld;
    private final Map<Chunk,BitSet[]> chunks=new HashMap<>();
    private int scanTick;
    private BlockPos pending;
    private net.minecraft.block.state.IBlockState previous;
    private long pendingAt,shownAt;
    private net.minecraft.entity.player.EntityPlayer inventoryPlayer;
    private boolean inventoryWasDead;
    private final Set<String> inventoryBlocks=new HashSet<>();
    static int layer(int limit){return limit>=1&&limit<=255?limit-1:-1;}

    @SubscribeEvent public void receive(ClientChatReceivedEvent event){
        if(event.type==2||!ServerScope.active()||ChatRules.playerMessage(event.message.getFormattedText()))return;
        String text=ChatRules.plain(event.message.getUnformattedText()).trim();
        String name=MapPreview.serverQuery(text);if(name==null||name.equals(currentName))return;
        currentName=name;map=null;chunks.clear();pending=null;shownAt=0;int token=++request;
        MapInfo local=MapInfo.findLocal(name);
        if(local!=null&&local.exact(name)){map=local;return;}
        // shortcut: remote maps are resolved once in the background; unknown or region-only limits have no overlay.
        MapRemote.lookup(name,found->{if(request==token)map=found;});
    }
    @SubscribeEvent public void unload(WorldEvent.Unload event){
        if(!event.world.isRemote)return;
        scannedWorld=null;chunks.clear();pending=null;shownAt=0;inventoryPlayer=null;inventoryBlocks.clear();inventoryWasDead=false;
        // Hub -> match world unloads too; keep the announced map so the overlay survives the teleport.
        if(!ServerScope.active()){request++;currentName=null;map=null;}
    }
    static boolean nearLimit(int y,int limit){return limit>=0&&Math.abs(y-limit)<=4;}
    /** Chunk rescans are throttled to every fourth client tick (~150ms) to keep the overlay cheap. */
    static boolean scanDue(int tick){return tick>=0&&(tick&3)==0;}
    @SubscribeEvent public void interact(PlayerInteractEvent event){
        Minecraft mc=MinecraftAccess.get();MapInfo selected=map;
        if(!UtilitySettings.heightLimitOverlay||!ServerScope.active()||selected==null||event.world!=mc.theWorld||event.entityPlayer!=mc.thePlayer||event.action!=PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK||event.isCanceled()||event.face==null||event.pos==null||mc.thePlayer.getHeldItem()==null||!(mc.thePlayer.getHeldItem().getItem() instanceof ItemBlock))return;
        BlockPos target=event.world.getBlockState(event.pos).getBlock().isReplaceable(event.world,event.pos)?event.pos:event.pos.offset(event.face);
        if(!nearLimit(target.getY(),layer(selected.maxBuildHeight)))return;
        if(!event.world.getBlockState(target).getBlock().isReplaceable(event.world,target))return;
        pending=target;previous=event.world.getBlockState(target);pendingAt=System.nanoTime();
    }
    @SubscribeEvent public void chunkLoad(ChunkEvent.Load event){
        if(event.world.isRemote&&map!=null)chunks.put(event.getChunk(),null); // ChunkEvent.Load precedes packet data; snapshot on the next client tick.
    }
    @SubscribeEvent public void chunkUnload(ChunkEvent.Unload event){if(event.world.isRemote)chunks.remove(event.getChunk());}
    // shortcut: client block states have no placer identity; server-created blocks can appear placed until placement packets provide attribution.
    static void update(BitSet previous,BitSet added,BitSet current){
        BitSet fresh=(BitSet)current.clone();fresh.andNot(previous);
        added.and(current);added.or(fresh);
        previous.clear();previous.or(current);
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        UtilitySettings.load();
        Minecraft mc=MinecraftAccess.get();MapInfo selected=map;
        if(!UtilitySettings.heightLimitOverlay){pending=null;return;}
        if(mc.theWorld==null||selected==null||!ServerScope.active())return;
        if(mc.thePlayer!=inventoryPlayer||inventoryWasDead&&!mc.thePlayer.isDead){
            refreshInventory(mc.thePlayer);inventoryPlayer=mc.thePlayer;
        }
        inventoryWasDead=mc.thePlayer.isDead;
        if(selected.kitBlocks.isEmpty()&&inventoryBlocks.isEmpty())return;
        int y=layer(selected.maxBuildHeight);if(y<0)return;
        boolean placed=false;
        if(pending!=null){
            net.minecraft.block.state.IBlockState current=mc.theWorld.getBlockState(pending);
            if(!current.equals(previous)&&current.getBlock()!=Blocks.air){
                if(kitBlock(selected,current.getBlock()))shownAt=System.nanoTime();
                markPlaced(pending,y);pending=null;scanTick=0;placed=true;
            }
            else if(System.nanoTime()-pendingAt>1000000000L)pending=null;
        }
        if(scannedWorld!=mc.theWorld||layer!=y){chunks.clear();scannedWorld=mc.theWorld;layer=y;scanTick=0;}
        if(mc.getRenderViewEntity()==null)return;
        // Rescan right after a placement resolves; otherwise throttle the full chunk sweep to every fourth client tick.
        if(!placed&&!scanDue(scanTick))return;scanTick++;
        int cx=(int)Math.floor(mc.getRenderViewEntity().posX)>>4,cz=(int)Math.floor(mc.getRenderViewEntity().posZ)>>4;
        int radius=mc.gameSettings.renderDistanceChunks+1;
        BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();
        for(int x=cx-radius;x<=cx+radius;x++)for(int z=cz-radius;z<=cz+radius;z++){
            if(mc.theWorld.isBlockLoaded(pos.set(x<<4,y,z<<4))){
                Chunk chunk=mc.theWorld.getChunkFromChunkCoords(x,z);
                if(!chunks.containsKey(chunk))chunks.put(chunk,null);
            }
        }
        for(Map.Entry<Chunk,BitSet[]> entry:chunks.entrySet()){
            Chunk chunk=entry.getKey();BitSet current=new BitSet(256);
            for(int x=0;x<16;x++)for(int z=0;z<16;z++){
                net.minecraft.block.Block block=chunk.getBlockState(pos.set(x,y,z)).getBlock();
                if(block!=Blocks.air&&kitBlock(selected,block))current.set(x*16+z);
            }
            BitSet[] state=entry.getValue();
            if(state==null)entry.setValue(new BitSet[]{current,(BitSet)current.clone()});
            else {state[0].clear();state[0].or(current);state[1].clear();state[1].or(current);}
        }
    }
    // Own placements are recorded directly: a chunk snapshot may be taken after terrain replaces the air we saw.
    private void markPlaced(BlockPos pos,int y){
        Chunk chunk=scannedWorld==null?null:scannedWorld.getChunkFromBlockCoords(pos);
        if(chunk==null)return;
        MapInfo map=this.map;
        if(map==null||!kitBlock(map,scannedWorld.getBlockState(pos).getBlock()))return;
        BitSet[] state=chunks.get(chunk);
        if(state==null){
            BitSet current=new BitSet(256);
            for(int x=0;x<16;x++)for(int z=0;z<16;z++){
                net.minecraft.block.Block block=chunk.getBlockState(new BlockPos(x,y,z)).getBlock();
                if(block!=Blocks.air&&kitBlock(map,block))current.set(x*16+z);
            }
            state=new BitSet[]{current,(BitSet)current.clone()};chunks.put(chunk,state);
        }
        state[1].set((pos.getX()&15)*16+(pos.getZ()&15));
    }
    static float fade(long age,int seconds){return Math.max(0F,1F-age/(seconds*1000000000F));}
    static HudLayout.Rect bounds(){
        float scale=UtilitySettings.heightLimitSize/100F;
        return HudLayout.bounds(HudLayout.HEIGHT,(int)Math.ceil(INDICATOR_WIDTH*scale),(int)Math.ceil(INDICATOR_HEIGHT*scale));
    }
    static void drawPreview(){drawBars(0xFFFFFFFF,false);}
    private static String blockKey(net.minecraft.block.Block block){
        Object name=net.minecraft.block.Block.blockRegistry.getNameForObject(block);
        return name==null?"":MapInfo.norm(name.toString().replace("minecraft:",""));
    }
    private boolean kitBlock(MapInfo map,net.minecraft.block.Block block){
        String key=blockKey(block);
        return map.kitBlocks.contains(key)||inventoryBlocks.contains(key)||(key.equals("stainedhardenedclay")&&(map.kitBlocks.contains("stainedclay")||inventoryBlocks.contains("stainedclay")));
    }
    private void refreshInventory(net.minecraft.entity.player.EntityPlayer player){
        inventoryBlocks.clear();
        for(net.minecraft.item.ItemStack stack:player.inventory.mainInventory){
            if(stack==null||!(stack.getItem() instanceof ItemBlock))continue;
            net.minecraft.block.Block block=Block.getBlockFromItem(stack.getItem());
            if(block!=null&&block!=Blocks.air)inventoryBlocks.add(blockKey(block));
        }
    }
    @SubscribeEvent public void indicator(RenderGameOverlayEvent.Post event){
        Minecraft mc=MinecraftAccess.get();MapInfo selected=map;
        if(event.type!=RenderGameOverlayEvent.ElementType.CHAT||shownAt==0||!UtilitySettings.heightLimitOverlay||!UtilitySettings.heightLimitIndicator||!ServerScope.active()||selected==null||mc.thePlayer==null||mc.gameSettings.hideGUI)return;
        int limit=layer(selected.maxBuildHeight),height=(int)Math.floor(mc.thePlayer.posY);
        float opacity=fade(System.nanoTime()-shownAt,UtilitySettings.heightLimitIndicatorSeconds);
        if(opacity<=0||!nearLimit(height,limit))return;
        drawBars((int)(255*opacity)<<24,height>=limit);
    }
    private static void drawBars(int alpha,boolean warning){
        HudLayout.Rect box=bounds();float scale=UtilitySettings.heightLimitSize/100F;
        GlStateManager.pushMatrix();GlStateManager.translate(box.x,box.y,0);GlStateManager.scale(scale,scale,1);
        int x=0,y=0;
        for(int i=0;i<5;i++){
            int width=14+i*9;
            int color=warning?0xFF5555:0x888888;
            Gui.drawRect(x+2,y+i*5,x+2+width,y+i*5+3,alpha|color);
        }
        GlStateManager.popMatrix();
    }
    @SubscribeEvent public void render(RenderWorldLastEvent event){
        UtilitySettings.load();MapInfo selected=map;Minecraft mc=MinecraftAccess.get();
        if(!UtilitySettings.heightLimitOverlay||UtilitySettings.heightLimitOpacity==0||!ServerScope.active()||selected==null||mc.theWorld==null||scannedWorld!=mc.theWorld)return;
        int y=layer(selected.maxBuildHeight);Entity camera=mc.getRenderViewEntity();
        if(y<0||layer!=y||camera==null||Math.abs(camera.posY-y)>40)return;
        boolean any=false;for(BitSet[] state:chunks.values())if(state!=null&&!state[1].isEmpty()){any=true;break;}
        if(!any)return;
        float partial=event.partialTicks;
        double cx=camera.lastTickPosX+(camera.posX-camera.lastTickPosX)*partial;
        double cy=camera.lastTickPosY+(camera.posY-camera.lastTickPosY)*partial;
        double cz=camera.lastTickPosZ+(camera.posZ-camera.lastTickPosZ)*partial;
        boolean texture=GL11.glIsEnabled(GL11.GL_TEXTURE_2D),lighting=GL11.glIsEnabled(GL11.GL_LIGHTING),blend=GL11.glIsEnabled(GL11.GL_BLEND),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),mask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int src=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dst=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),srcA=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),dstA=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        GlStateManager.pushMatrix();
        try{
            GlStateManager.translate(-cx,-cy,-cz);GlStateManager.disableLighting();GlStateManager.disableTexture2D();GlStateManager.disableCull();GlStateManager.enableDepth();GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.depthMask(false);
            Tessellator tess=Tessellator.getInstance();WorldRenderer wr=tess.getWorldRenderer();wr.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_COLOR);
            int radius=(mc.gameSettings.renderDistanceChunks+1)*16,alpha=UtilitySettings.heightLimitOpacity*255/100;
            for(Map.Entry<Chunk,BitSet[]> entry:chunks.entrySet()){
                Chunk chunk=entry.getKey();BitSet[] state=entry.getValue();
                if(state==null||Math.abs(chunk.xPosition*16+8-cx)>radius||Math.abs(chunk.zPosition*16+8-cz)>radius)continue;
                for(int bit=state[1].nextSetBit(0);bit>=0;bit=state[1].nextSetBit(bit+1)){
                int blockX=chunk.xPosition*16+(bit>>4),blockZ=chunk.zPosition*16+(bit&15);
                BlockPos pos=new BlockPos(blockX,y,blockZ);
                double x=blockX,z=blockZ,right=blockX+1,back=blockZ+1,top=y+1.005,bottom=y-.005;
                face(wr,alpha,x,top,z,right,top,z,right,top,back,x,top,back);
                if(!UtilitySettings.heightLimitTopOnly){
                    if(!hasNeighbor(blockX,blockZ-1,y))face(wr,alpha,x,bottom,z-.005,right,bottom,z-.005,right,top,z-.005,x,top,z-.005);
                    if(!hasNeighbor(blockX,blockZ+1,y))face(wr,alpha,right,bottom,back+.005,x,bottom,back+.005,x,top,back+.005,right,top,back+.005);
                    if(!hasNeighbor(blockX-1,blockZ,y))face(wr,alpha,x-.005,bottom,back,x-.005,bottom,z,x-.005,top,z,x-.005,top,back);
                    if(!hasNeighbor(blockX+1,blockZ,y))face(wr,alpha,right+.005,bottom,z,right+.005,bottom,back,right+.005,top,back,right+.005,top,z);
                }
                }
            }
            tess.draw();
        }finally{
            GlStateManager.popMatrix();GlStateManager.depthMask(mask);
            if(texture)GlStateManager.enableTexture2D();else GlStateManager.disableTexture2D();
            if(lighting)GlStateManager.enableLighting();else GlStateManager.disableLighting();
            if(cull)GlStateManager.enableCull();else GlStateManager.disableCull();
            if(depth)GlStateManager.enableDepth();else GlStateManager.disableDepth();
            if(blend)GlStateManager.enableBlend();else GlStateManager.disableBlend();
            GlStateManager.tryBlendFuncSeparate(src,dst,srcA,dstA);GlStateManager.color(1,1,1,1);
        }
    }
    private static void face(WorldRenderer wr,int alpha,double ax,double ay,double az,double bx,double by,double bz,double cx,double cy,double cz,double dx,double dy,double dz){
        wr.pos(ax,ay,az).color(0,0,0,alpha).endVertex();wr.pos(bx,by,bz).color(0,0,0,alpha).endVertex();
        wr.pos(cx,cy,cz).color(0,0,0,alpha).endVertex();wr.pos(dx,dy,dz).color(0,0,0,alpha).endVertex();
    }
    private boolean hasNeighbor(int x,int z,int y){
        Chunk chunk=mcChunk(x,z);if(chunk==null)return false;
        BitSet[] state=chunks.get(chunk);return state!=null&&state[1].get((x&15)*16+(z&15));
    }
    private Chunk mcChunk(int x,int z){
        Minecraft mc=MinecraftAccess.get();return mc.theWorld==null||!mc.theWorld.isBlockLoaded(new BlockPos(x,0,z))?null:mc.theWorld.getChunkFromChunkCoords(x>>4,z>>4);
    }
}
