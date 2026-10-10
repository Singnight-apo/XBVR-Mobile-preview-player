package top.liuwei.xbvr.data;

import java.io.IOException;
import okhttp3.HttpUrl;
import top.liuwei.xbvr.domain.MediaDetailsRepository;
import top.liuwei.xbvr.domain.Models.Detail;
import top.liuwei.xbvr.domain.Models.Source;
import top.liuwei.xbvr.domain.ResourceIdentity;

/** File-specific playback without scene fallback or server favorite writes. */
public final class FilePreviewRepository implements MediaDetailsRepository {
    private final XbvrApi api;
    public FilePreviewRepository(XbvrApi api) {this.api=api;}
    @Override public Detail detail(String url) throws Exception {
        HttpUrl target=HttpUrl.parse(url);
        String prefix=HttpUrl.get(api.base).encodedPath().replaceAll("/$","")+"/deovr/file/";
        if(target==null||!XbvrProtocol.sameOrigin(api.base,url)||!target.encodedPath().startsWith(prefix))
            throw new IOException("Invalid file preview address");
        String id=target.encodedPath().substring(prefix.length());
        if(!id.matches("[1-9][0-9]*"))throw new IOException("Invalid preview file ID");
        Detail detail=XbvrProtocol.detail(api.json(url,false,null),api.base,false);
        for(Source source:detail.sources) {
            if(!id.equals(XbvrProtocol.fileId(source.url))||!XbvrProtocol.sameOrigin(api.base,source.url)
                    ||!ResourceIdentity.of(url).equals(ResourceIdentity.of(source.url)))
                throw new IOException("Server returned a different file for preview");
            source.url=HttpUrl.get(source.url).newBuilder().setQueryParameter("dnt","true").build().toString();
            if(source.filename.isBlank())source.filename=detail.title;
        }
        if(detail.sources.isEmpty())throw new IOException("File preview is unavailable; check the XBVR player interface");
        detail.writeFavorite=false;detail.hsUrl="";
        return detail;
    }
    @Override public void favorite(Detail detail,boolean value) throws IOException {
        throw new IOException("Server favorites are unavailable in file preview");
    }
}
