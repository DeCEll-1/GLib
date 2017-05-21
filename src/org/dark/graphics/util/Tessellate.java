package org.dark.graphics.util;

import com.fs.starfarer.api.combat.BoundsAPI;
import com.fs.starfarer.api.combat.BoundsAPI.SegmentAPI;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;
import org.lwjgl.util.glu.GLUtessellatorCallbackAdapter;
import org.lwjgl.util.glu.tessellation.GLUtessellatorImpl;
import org.lwjgl.util.vector.Vector2f;

public class Tessellate {

    public static void clearCache() {
        /* TODO: Implement some kind of caching feature for this stupid bullshit */
    }

    public static void render(BoundsAPI bounds, float r, float g, float b, String id) {
        GLUtessellatorImpl tesselator = (GLUtessellatorImpl) GLU.gluNewTess();
        TessCallbackV2 callback = new TessCallbackV2();
        tesselator.gluTessCallback(GLU.GLU_TESS_VERTEX, callback);
        tesselator.gluTessCallback(GLU.GLU_TESS_BEGIN, callback);
        tesselator.gluTessCallback(GLU.GLU_TESS_END, callback);
        tesselator.gluTessCallback(GLU.GLU_TESS_COMBINE, callback);

        tesselator.gluTessProperty(GLU.GLU_TESS_WINDING_RULE, GLU.GLU_TESS_WINDING_ODD);
        tesselator.gluTessBeginPolygon(null);
        tesselator.gluTessBeginContour();

        final List<SegmentAPI> segments = bounds.getSegments();
        final List<Vector2f> points = new ArrayList<>(segments.size());
        for (SegmentAPI segment : segments) {
            points.add(segment.getP1());
        }
        double[][] data = new double[points.size()][6];
        for (int i = 0; i < points.size(); i++) {
            Vector2f v = points.get(i);
            data[i][0] = v.x;
            data[i][1] = v.y;
            data[i][2] = 0f;
            data[i][3] = r;
            data[i][4] = g;
            data[i][5] = b;
        }

        for (int i = 0; i < points.size(); i++) {
            tesselator.gluTessVertex(data[i], 0, new VertexDataV2(data[i])); //store the vertex
        }

        tesselator.gluTessEndContour();
        tesselator.gluTessEndPolygon();
        tesselator.gluDeleteTess();
    }

    public static class TessCallbackV2 extends GLUtessellatorCallbackAdapter {

        @Override
        public void begin(int type) {
            GL11.glBegin(type);
        }

        @Override
        public void combine(double[] coords, Object[] data, float[] weight, Object[] outData) {
            for (int i = 0; i < outData.length; i++) {
                double[] combined = new double[6];
                combined[0] = coords[0];
                combined[1] = coords[1];
                combined[2] = coords[2];
                combined[3] = 1;
                combined[4] = 1;
                combined[5] = 1;

                outData[i] = new VertexDataV2(combined);
            }
        }

        @Override
        public void end() {
            GL11.glEnd();
        }

        @Override
        public void vertex(Object vertexData) {
            VertexDataV2 vertex = (VertexDataV2) vertexData;

            GL11.glVertex3d(vertex.data[0], vertex.data[1], vertex.data[2]);
            GL11.glColor3d(vertex.data[3], vertex.data[4], vertex.data[5]);
        }
    }

    public static class VertexDataV2 {

        public double[] data;

        VertexDataV2(double[] data) {
            this.data = data;
        }
    }
}
