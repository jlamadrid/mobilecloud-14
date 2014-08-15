package org.magnum.dataup.web.rest;

import org.magnum.dataup.util.VideoFileManager;
import org.magnum.dataup.web.api.VideoSvcApi;
import org.magnum.dataup.model.Video;
import org.magnum.dataup.model.VideoStatus;
import org.magnum.dataup.web.util.ResourceNotFoundException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Reference: uploading files --> http://spring.io/guides/gs/uploading-files/
 * NOTES:
 *  - need to implement contract defined in VideoSvcApi
 *
 * Created by JL25292 on 8/13/2014.
 */
@Controller
public class VideoServiceController {

    // An in-memory list that the servlet uses to store the videos that are sent to it by clients
    private ConcurrentMap<Long, Video> videos = new ConcurrentHashMap<Long, Video>();

    final AtomicLong counter = new AtomicLong();

    /**
     * GET /video
     *   - Returns the list of videos that have been added to the
     *     server as JSON. The list of videos does not have to be
     *     persisted across restarts of the server. The list of
     *     Video objects should be able to be unmarshalled by the
     *     client into a Collection<Video>.
     * @return
     */
    @RequestMapping(value=VideoSvcApi.VIDEO_SVC_PATH, method=RequestMethod.GET)
    public @ResponseBody Collection<Video> getVideoList(){
        return videos.values();
    }

    /**
     * POST /video
     *   - The video data is provided as an application/json request
     *     body. The JSON should generate a valid instance of the
     *     Video class when deserialized by Spring's default
     *     Jackson library.
     *   - Returns the JSON representation of the Video object that
     *     was stored along with any updates to that object.
     *     --The server should generate a unique identifier for the Video
     *     object and assign it to the Video by calling its setId(...)
     *     method. The returned Video JSON should include this server-generated
     *     identifier so that the client can refer to it when uploading the
     *     binary mpeg video content for the Video.
     *    -- The server should also generate a "data url" for the
     *     Video. The "data url" is the url of the binary data for a
     *     Video (e.g., the raw mpeg data). The URL should be the *full* URL
     *     for the video and not just the path. You can use a method like the
     *     following to figure out the name of your server:
     *
     *     	private String getUrlBaseForLocalServer() {
     *		   HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
     *		   String base = "http://"+request.getServerName()+((request.getServerPort() != 80) ? ":"+request.getServerPort() : "");
     *		   return base;
     *		}
     *
     * @param v
     * @return
     */
    @RequestMapping(value= VideoSvcApi.VIDEO_SVC_PATH, method=RequestMethod.POST)
    public @ResponseBody Video addVideo(@RequestBody Video v){

        //generate a unique id
        long id = counter.incrementAndGet();

        v.setId(id);
        v.setDataUrl(getDataUrl(v.getId()));
        videos.put(id,v);

        return v;
    }

    /**
     * POST /video/{id}/data
     *   - The binary mpeg data for the video should be provided in a multipart
     *     request as a part with the key "data". The id in the path should be
     *     replaced with the unique identifier generated by the server for the
     *     Video. A client MUST *create* a Video first by sending a POST to /video
     *     and getting the identifier for the newly created Video object before
     *     sending a POST to /video/{id}/data.
     *
     *
     * @param id
     * @param data
     * @return
     */
    @RequestMapping(value= VideoSvcApi.VIDEO_DATA_PATH, method=RequestMethod.POST)
    public @ResponseBody VideoStatus handleFileUpload(@PathVariable long id,
                                                      @RequestParam("data") MultipartFile data) throws IOException {


        if(!videos.containsKey(id)){
            throw new ResourceNotFoundException();
        }

        VideoFileManager videoMngr = VideoFileManager.get();

        /*
        if (!data.isEmpty()) {
            try {
                byte[] bytes = data.getBytes();
                BufferedOutputStream stream = new BufferedOutputStream(
                        new FileOutputStream(new File("/Users/Juan/dev/" + id + "-uploaded"))
                );

                stream.write(bytes);
                stream.close();
                //return "You successfully uploaded " + id + " into " + id + "-uploaded !";
                return new VideoStatus(VideoStatus.VideoState.READY);
            } catch (Exception e) {
                return new VideoStatus(VideoStatus.VideoState.PROCESSING);
                //return "You failed to upload " + id + " => " + e.getMessage();
            }
        } else {
            //return "You failed to upload " + id + " because the file was empty.";
            return new VideoStatus(VideoStatus.VideoState.PROCESSING);
        }
        */

        byte[] bytes = data.getBytes();
        InputStream inputStream = new ByteArrayInputStream(bytes);

        videoMngr.saveVideoData(videos.get(id), inputStream);

        return new VideoStatus(VideoStatus.VideoState.READY);
    }

    /**
     * This endpoint should return the video data that has been associated with
     * a Video object or a 404 if no video data has been set yet. The URL scheme
     * is the same as in the method above and assumes that the client knows the ID
     * of the Video object that it would like to retrieve video data for.
     *
     * This method uses Retrofit's @Streaming annotation to indicate that the
     * method is going to access a large stream of data (e.g., the mpeg video
     * data on the server). The client can access this stream of data by obtaining
     * an InputStream from the Response as shown below:
     *
     * VideoSvcApi client = ... // use retrofit to create the client
     * Response response = client.getData(someVideoId);
     * InputStream videoDataStream = response.getBody().in();
     * @param id
     * @param response
     */
    @RequestMapping(value = VideoSvcApi.VIDEO_DATA_PATH, method = RequestMethod.GET)
    public void streamFile(@PathVariable long id, HttpServletResponse response) throws IOException {

        //TODO: check to make sure a video with given id exists, if not throw ex

        if(!videos.containsKey(id)){
            throw new ResourceNotFoundException();
        }

        VideoFileManager videoMngr = VideoFileManager.get();

        /*
        try {
            // get your file as InputStream
            InputStream is = new BufferedInputStream(
                    new FileInputStream(new File("/Users/Juan/dev/" + id + "-uploaded"))
            );
            // copy it to response's OutputStream
            IOUtils.copy(is, response.getOutputStream());
            response.flushBuffer();
        } catch (IOException ex) {
            throw new RuntimeException("IOError writing file to output stream");
        }
        */

        videoMngr.copyVideoData(videos.get(id), response.getOutputStream());

    }

    private String getDataUrl(long videoId){
        String url = getUrlBaseForLocalServer() + "/video/" + videoId + "/data";
        return url;
    }

    private String getUrlBaseForLocalServer() {
        HttpServletRequest request =
                ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String base =
                "http://"+request.getServerName()
                        + ((request.getServerPort() != 80) ? ":"+request.getServerPort() : "");
        return base;
    }

}
