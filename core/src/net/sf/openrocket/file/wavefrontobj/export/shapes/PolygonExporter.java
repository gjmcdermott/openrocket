package net.sf.openrocket.file.wavefrontobj.export.shapes;

import com.sun.istack.NotNull;
import net.sf.openrocket.file.wavefrontobj.CoordTransform;
import net.sf.openrocket.file.wavefrontobj.DefaultObj;
import net.sf.openrocket.file.wavefrontobj.DefaultObjFace;
import net.sf.openrocket.file.wavefrontobj.ObjUtils;

public class PolygonExporter {

    /**
     * Add a polygon mesh to the obj. It is drawn in the OpenRocket XY plane with the bottom left corner at the OpenRocket origin.
     * @param obj The obj to add the mesh to
     * @param transformer The coordinate system transformer to use to switch from the OpenRocket coordinate system to a custom OBJ coordinate system
     * @param groupName The name of the group to add the mesh to, or null if no group should be added (use the active group)
     * @param pointLocationsX The x locations of the points --> NOTE: points should follow a clockwise direction
     * @param pointLocationsY The y locations of the points --> NOTE: points should follow a clockwise direction
     * @param thickness The thickness of the polygon
     */
    public static void addPolygonMesh(@NotNull DefaultObj obj, @NotNull CoordTransform transformer, String groupName,
                                      float[] pointLocationsX, float[] pointLocationsY, float thickness) {
        verifyPoints(pointLocationsX, pointLocationsY);

        // Set the new group
        if (groupName != null) {
            obj.setActiveGroupNames(groupName);
        }

        // Other meshes may have been added to the group, so we need to keep track of the starting indices
        int startIdx = obj.getNumVertices();
        int normalsStartIdx = obj.getNumNormals();

        // NOTE: "front" is the side view with the normal pointing towards the viewer, "back" is the side with the normal pointing away from the viewer

        obj.addNormal(transformer.convertLocWithoutOriginOffs(0, 0, -1));       // Front faces normal
        obj.addNormal(transformer.convertLocWithoutOriginOffs(0, 0, 1));        // Back faces normal

        // Generate front face vertices
        for (int i = 0; i < pointLocationsX.length; i++) {
            obj.addVertex(transformer.convertLoc(pointLocationsX[i], pointLocationsY[i], -thickness/2));
        }

        // Generate back face vertices
        for (int i = 0; i < pointLocationsX.length; i++) {
            obj.addVertex(transformer.convertLoc(pointLocationsX[i], pointLocationsY[i], thickness/2));
        }

        // Create front face
        int[] vertexIndices = new int[pointLocationsX.length];
        int[] normalIndices = new int[pointLocationsX.length];
        for (int i = 0; i < pointLocationsX.length; i++) {
            vertexIndices[i] = pointLocationsX.length-1 - i;
            normalIndices[i] = normalsStartIdx;
        }
        ObjUtils.offsetIndex(vertexIndices, startIdx);
        DefaultObjFace face = new DefaultObjFace(vertexIndices, null, normalIndices);
        obj.addFace(face);

        // Create back face
        vertexIndices = new int[pointLocationsX.length];
        normalIndices = new int[pointLocationsX.length];
        for (int i = 0; i < pointLocationsX.length; i++) {
            vertexIndices[i] = pointLocationsX.length + i;
            normalIndices[i] = normalsStartIdx + 1;
        }
        ObjUtils.offsetIndex(vertexIndices, startIdx);
        face = new DefaultObjFace(vertexIndices, null, normalIndices);
        obj.addFace(face);

        // Create side faces
        for (int i = 0; i < pointLocationsX.length; i++) {
            int nextIdx = (i + 1) % pointLocationsX.length;
            vertexIndices = new int[]{
                    i,                                          // Bottom-left of quad
                    nextIdx,                                    // Top-left of quad
                    pointLocationsX.length + nextIdx,           // Top-right of quad
                    pointLocationsX.length + i                  // Bottom-right of quad
            };
            ObjUtils.offsetIndex(vertexIndices, startIdx);

            // Calculate normals for side faces
            final float dx = pointLocationsY[nextIdx] - pointLocationsY[i];
            final float dy = pointLocationsX[nextIdx] - pointLocationsX[i];

            // Perpendicular vector in 2D (for clockwise vertices)
            final float nx = -dy;
            final float ny = dx;

            // Add the normal to the object
            obj.addNormal(nx, ny, 0);

            normalIndices = new int[]{i, i, i, i};
            ObjUtils.offsetIndex(normalIndices, normalsStartIdx + 2);       // Offset by 2 to skip the bottom and top faces normals

            face = new DefaultObjFace(vertexIndices, null, normalIndices);
            obj.addFace(face);
        }
    }

    private static void verifyPoints(float[] pointLocationsX, float[] pointLocationsY) {
        if (pointLocationsX.length != pointLocationsY.length) {
            throw new IllegalArgumentException("pointLocationsX and pointLocationsY must be the same length");
        }

        if (pointLocationsX.length < 3) {
            throw new IllegalArgumentException("At least 3 points are required to create a polygon");
        }

        if (Float.compare(pointLocationsX[pointLocationsX.length-1], pointLocationsX[0]) == 0 &&
                Float.compare(pointLocationsY[pointLocationsY.length-1], pointLocationsY[0]) == 0) {
            throw new IllegalArgumentException("The first and last points must be different");
        }
    }
}
