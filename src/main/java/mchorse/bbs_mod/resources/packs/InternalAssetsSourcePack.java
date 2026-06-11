package mchorse.bbs_mod.resources.packs;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.resources.ISourcePack;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.StringUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

public class InternalAssetsSourcePack implements ISourcePack
{
    private String prefix;
    private String internalPrefix;
    private Class clazz;

    public InternalAssetsSourcePack()
    {
        this(Link.ASSETS, "assets/bbs/assets", InternalAssetsSourcePack.class);
    }

    public InternalAssetsSourcePack(String prefix, String internalPrefix, Class clazz)
    {
        this.prefix = prefix;
        this.internalPrefix = internalPrefix;
        this.clazz = clazz;
    }

    @Override
    public String getPrefix()
    {
        return this.prefix;
    }

    @Override
    public boolean hasAsset(Link link)
    {
        return this.clazz.getClassLoader().getResource(this.internalPrefix + "/" + link.path) != null;
    }

    @Override
    public InputStream getAsset(Link link) throws IOException
    {
        return this.clazz.getClassLoader().getResourceAsStream(this.internalPrefix + "/" + link.path);
    }

    @Override
    public File getFile(Link link)
    {
        return null;
    }

    @Override
    public Link getLink(File file)
    {
        return null;
    }

    @Override
    public void getLinksFromPath(Collection<Link> links, Link link, boolean recursive)
    {
        ModContainer container = FabricLoader.getInstance().getModContainer(BBSMod.MOD_ID).orElse(null);

        if (container == null)
        {
            return;
        }

        String path = link.path;

        if (path.endsWith("/"))
        {
            path = path.substring(0, path.length() - 1);
        }

        /* Walk the mod's resource roots through NIO so it works regardless of the
         * underlying file system. Reading the jar as a java.io.File (via Path.toFile())
         * breaks on Forge/NeoForge, where the mod is mounted in a non-default (union)
         * file system. In a development environment getRootPaths() also exposes the
         * separate classes/resources output folders, so no special casing is needed. */
        for (Path root : container.getRootPaths())
        {
            Path folder = root.resolve(this.internalPrefix + "/" + path);

            if (Files.isDirectory(folder))
            {
                this.getLinksFromPath(folder, links, path, recursive ? 9999 : 1);
            }
        }
    }

    private void getLinksFromPath(Path folder, Collection<Link> links, String prefix, int depth)
    {
        depth -= 1;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder))
        {
            for (Path file : stream)
            {
                boolean directory = Files.isDirectory(file);
                String path = StringUtils.combinePaths(prefix, file.getFileName().toString());

                if (directory && depth > 0)
                {
                    this.getLinksFromPath(file, links, path, depth);
                }

                links.add(new Link(this.prefix, path + (directory ? "/" : "")));
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }
}
