package net.sf.openrocket.file.wavefrontobj.export.components;

import com.sun.istack.NotNull;
import net.sf.openrocket.file.wavefrontobj.CoordTransform;
import net.sf.openrocket.file.wavefrontobj.DefaultObj;
import net.sf.openrocket.file.wavefrontobj.DefaultObjFace;
import net.sf.openrocket.file.wavefrontobj.ObjUtils;
import net.sf.openrocket.rocketcomponent.MassObject;
import net.sf.openrocket.util.Coordinate;
import net.sf.openrocket.util.RocketComponentUtils;

public class MassObjectExporter extends RocketComponentExporter<MassObject> {
    public MassObjectExporter(@NotNull DefaultObj obj, @NotNull CoordTransform transformer, MassObject component,
                              String groupName, ObjUtils.LevelOfDetail LOD) {
        super(obj, transformer, component, groupName, LOD);
    }

    @Override
    public void addToObj() {
        obj.setActiveGroupNames(groupName);

        final Coordinate[] locations = component.getComponentLocations();
        final int numSides = LOD.getValue() / 2;
        final int numStacks = LOD.getValue() / 2;

        // Generate the mesh
        for (Coordinate location : locations) {
            generateMesh(numSides, numStacks, location);
        }
    }

    private void generateMesh(int numSides, int numStacks, Coordinate location) {
        // Other meshes may have been added to the obj, so we need to keep track of the starting indices
        int startIdx = obj.getNumVertices();
        int normalsStartIdx = obj.getNumNormals();
        double dx = component.getLength() / numStacks;
        double da = 2.0f * Math.PI / numSides;

        // Generate vertices and normals
        for (int j = 0; j <= numStacks; j++) {
            double x = j * dx;

            if (j == 0 || j == numStacks) {
                // Add a center vertex
                obj.addVertex(transformer.convertLoc(x, 0, 0));
                obj.addNormal(transformer.convertLocWithoutOriginOffs(j == 0 ? -1 : 1, 0, 0));
            } else {
                // Add a vertex for each side
                for (int i = 0; i < numSides; i++) {
                    double angle = i * da;
                    double r = RocketComponentUtils.getMassObjectRadius(component, x);
                    double y = r * Math.cos(angle);
                    double z = r * Math.sin(angle);

                    obj.addVertex(transformer.convertLoc(x, y, z));

                    // Add normals
                    if (Double.compare(r, component.getRadius()) == 0) {        // If in cylindrical section, use cylinder normals
                        obj.addNormal(transformer.convertLocWithoutOriginOffs(0, y, z));
                    } else {
                        final double xCenter;
                        if (j <= numStacks/2) {
                            xCenter = RocketComponentUtils.getMassObjectArcHeight(component);
                        } else {
                            xCenter = component.getLength() - RocketComponentUtils.getMassObjectArcHeight(component);
                        }
                        obj.addNormal(transformer.convertLocWithoutOriginOffs(x - xCenter, y, z));     // For smooth shading
                    }
                }
            }
        }

        int endIdx = Math.max(obj.getNumVertices() - 1, startIdx);        // Clamp in case no vertices were added

        // Create bottom tip faces
        for (int i = 0; i < numSides; i++) {
            int nextIdx = (i + 1) % numSides;

            int[] vertexIndices = new int[] {
                    0,              // Center vertex
                    1 + i,
                    1 + nextIdx,
            };
            int[] normalIndices = vertexIndices.clone();   // For a smooth surface, the vertex and normal indices are the same

            ObjUtils.offsetIndex(normalIndices, normalsStartIdx);
            ObjUtils.offsetIndex(vertexIndices, startIdx);      // Only do this after normals are added, since the vertex indices are used for normals

            DefaultObjFace face = new DefaultObjFace(vertexIndices, null, normalIndices);
            obj.addFace(face);
        }

        // Create normal side faces
        for (int j = 0; j < numStacks-2; j++) {
            for (int i = 0; i < numSides; i++) {
                int nextIdx = (i + 1) % numSides;

                int[] vertexIndices = new int[] {
                        1 + j * numSides + i,
                        1 + (j + 1) * numSides + i,
                        1 + (j + 1) * numSides + nextIdx,
                        1 + j * numSides + nextIdx
                };
                int[] normalIndices = vertexIndices.clone();   // For a smooth surface, the vertex and normal indices are the same

                ObjUtils.offsetIndex(normalIndices, normalsStartIdx);
                ObjUtils.offsetIndex(vertexIndices, startIdx);      // Only do this after normals are added, since the vertex indices are used for normals

                DefaultObjFace face = new DefaultObjFace(vertexIndices, null, normalIndices);
                obj.addFace(face);
            }
        }

        // Create top tip faces
        final int normalEndIdx = obj.getNumNormals() - 1;
        for (int i = 0; i < numSides; i++) {
            int nextIdx = (i + 1) % numSides;
            int[] vertexIndices = new int[] {
                    endIdx,           // Center vertex
                    endIdx - numSides + nextIdx,
                    endIdx - numSides + i,
            };
            int[] normalIndices = new int[] {
                    normalEndIdx,           // Center vertex
                    normalEndIdx - numSides + nextIdx,
                    normalEndIdx - numSides + i,
            };

            // Don't offset! We reference from the last index

            DefaultObjFace face = new DefaultObjFace(vertexIndices, null, normalIndices);
            obj.addFace(face);
        }

        // Translate the mesh to the position in the rocket
        //      We will create an offset location that has the same effect as the axial rotation of the mass object
        Coordinate offsetLocation = getOffsetLocation(location);
        ObjUtils.translateVerticesFromComponentLocation(obj, transformer, startIdx, endIdx, offsetLocation);
    }

    private Coordinate getOffsetLocation(Coordinate location) {
        // ! This is all still referenced to the OpenRocket coordinate system, not the OBJ one
        final double radialPosition = component.getRadialPosition();
        final double radialDirection = component.getRadialDirection();
        final double x = location.x;
        final double y = location.y + radialPosition * Math.cos(radialDirection);
        final double z = location.z + radialPosition * Math.sin(radialDirection);
        return new Coordinate(x, y, z);
    }
}
