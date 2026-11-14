package otl.client;

import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.util.EnumFacing;

/** Snowball model that hands rendering to the football mesh only while a Touchdown map is running. */
final class FootballItemModel implements IBakedModel {
    private final IBakedModel base;
    FootballItemModel(IBakedModel base){this.base=base;}
    public List<BakedQuad> getFaceQuads(EnumFacing facing){return base.getFaceQuads(facing);}
    public List<BakedQuad> getGeneralQuads(){return base.getGeneralQuads();}
    public boolean isAmbientOcclusion(){return base.isAmbientOcclusion();}
    public boolean isGui3d(){return Touchdown.active()||base.isGui3d();}
    public boolean isBuiltInRenderer(){return Touchdown.active();}
    public TextureAtlasSprite getParticleTexture(){return base.getParticleTexture();}
    public ItemCameraTransforms getItemCameraTransforms(){return base.getItemCameraTransforms();}
}
