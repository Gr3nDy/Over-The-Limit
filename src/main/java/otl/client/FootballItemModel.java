package otl.client;

import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.model.IFlexibleBakedModel;
import net.minecraftforge.client.model.IPerspectiveAwareModel;
import org.apache.commons.lang3.tuple.Pair;
import javax.vecmath.Matrix4f;

/** Snowball model that hands rendering to the football mesh only while a Touchdown map is running. */
final class FootballItemModel implements IPerspectiveAwareModel {
    private final IBakedModel base;
    FootballItemModel(IBakedModel base){this.base=base;}
    public List<BakedQuad> getFaceQuads(EnumFacing facing){return base.getFaceQuads(facing);}
    public List<BakedQuad> getGeneralQuads(){return base.getGeneralQuads();}
    public boolean isAmbientOcclusion(){return base.isAmbientOcclusion();}
    public boolean isGui3d(){return Touchdown.modelActive()||base.isGui3d();}
    public boolean isBuiltInRenderer(){return Touchdown.modelActive();}
    public TextureAtlasSprite getParticleTexture(){return base.getParticleTexture();}
    public ItemCameraTransforms getItemCameraTransforms(){return base.getItemCameraTransforms();}
    public VertexFormat getFormat(){return base instanceof IFlexibleBakedModel?((IFlexibleBakedModel)base).getFormat():DefaultVertexFormats.ITEM;}
    public Pair<? extends IFlexibleBakedModel,Matrix4f> handlePerspective(ItemCameraTransforms.TransformType type){
        if(base instanceof IPerspectiveAwareModel){
            Pair<? extends IFlexibleBakedModel,Matrix4f> perspective=((IPerspectiveAwareModel)base).handlePerspective(type);
            return Touchdown.modelActive()?Pair.of(this,perspective.getRight()):perspective;
        }
        base.getItemCameraTransforms().applyTransform(type);
        return Pair.of(this,null);
    }
}
