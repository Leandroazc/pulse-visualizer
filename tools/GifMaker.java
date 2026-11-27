import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;

/**
 * Junta os quadros PNG gerados pelos testes de previa (pasta gif-frames) num GIF animado em loop.
 * Uso: java GifMaker.java <pasta-dos-quadros> <saida.gif> <atraso-em-centesimos> <largura>
 * Roda no compilar.bat com o JDK do projeto; o ImageIO nao existe nos testes Android.
 */
public class GifMaker {
    public static void main(String[] args) throws Exception {
        File dir = new File(args[0]);
        File out = new File(args[1]);
        int delayCs = Integer.parseInt(args[2]);
        int width = Integer.parseInt(args[3]);

        File[] frames = dir.listFiles((d, name) -> name.endsWith(".png"));
        if (frames == null || frames.length == 0) throw new IllegalStateException("sem quadros em " + dir);
        Arrays.sort(frames);

        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        out.delete();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.prepareWriteSequence(null);
            for (int i = 0; i < frames.length; i++) {
                BufferedImage src = ImageIO.read(frames[i]);
                int height = src.getHeight() * width / src.getWidth();
                BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                var g = img.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.drawImage(src, 0, 0, width, height, null);
                g.dispose();

                IIOMetadata meta = writer.getDefaultImageMetadata(
                    ImageTypeSpecifier.createFromRenderedImage(img), writer.getDefaultWriteParam());
                String format = meta.getNativeMetadataFormatName();
                IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree(format);

                IIOMetadataNode gce = child(root, "GraphicControlExtension");
                gce.setAttribute("disposalMethod", "none");
                gce.setAttribute("userInputFlag", "FALSE");
                gce.setAttribute("transparentColorFlag", "FALSE");
                gce.setAttribute("delayTime", Integer.toString(delayCs));
                gce.setAttribute("transparentColorIndex", "0");

                if (i == 0) {
                    IIOMetadataNode app = new IIOMetadataNode("ApplicationExtension");
                    app.setAttribute("applicationID", "NETSCAPE");
                    app.setAttribute("authenticationCode", "2.0");
                    app.setUserObject(new byte[] {1, 0, 0}); // repete sem parar
                    child(root, "ApplicationExtensions").appendChild(app);
                }
                meta.setFromTree(format, root);
                writer.writeToSequence(new IIOImage(img, null, meta), writer.getDefaultWriteParam());
            }
            writer.endWriteSequence();
        }
        writer.dispose();
        System.out.println("GIF: " + out + " (" + frames.length + " quadros, " + out.length() / 1024 + " KB)");
    }

    private static IIOMetadataNode child(IIOMetadataNode root, String name) {
        for (int i = 0; i < root.getLength(); i++) {
            if (root.item(i).getNodeName().equals(name)) return (IIOMetadataNode) root.item(i);
        }
        IIOMetadataNode node = new IIOMetadataNode(name);
        root.appendChild(node);
        return node;
    }
}
