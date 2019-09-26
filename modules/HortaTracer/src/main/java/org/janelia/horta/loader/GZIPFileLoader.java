package org.janelia.horta.loader;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.zip.GZIPInputStream;

import net.jpountz.lz4.LZ4FrameInputStream;
import org.apache.commons.io.FilenameUtils;

/**
 *
 * @author Christopher Bruns
 */
public class GZIPFileLoader implements FileTypeLoader
{

    @Override
    public boolean supports(DataSource source)
    {
        String ext = FilenameUtils.getExtension(source.getFileName()).toUpperCase();
        if (ext.equals("GZ"))
            return true;
        if (ext.equals("Z"))
            return true;
        return false;
    }

    @Override
    public boolean load(DataSource source, FileHandler handler) throws IOException
    {
        // Delegate to uncompressed datasource
        String uncompressedName = FilenameUtils.getBaseName(source.getFileName());
        DataSource uncompressed = new BasicDataSource(() -> {
            try {
                return new GZIPInputStream(source.openInputStream());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, uncompressedName);
        return handler.handleDataSource(uncompressed);
    }
    
}
