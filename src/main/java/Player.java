import com.mpatric.mp3agic.InvalidDataException;
import com.mpatric.mp3agic.UnsupportedTagException;
import javazoom.jl.decoder.*;
import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.FactoryRegistry;
import support.PlayerWindow;
import support.Song;

import javax.swing.*;
import javax.swing.event.MouseInputAdapter;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Player {

    private PlayerWindow Interface;

    /**
     * The MPEG audio bitstream.
     */
    private Bitstream bitstream;
    /**
     * The MPEG audio decoder.
     */
    private Decoder decoder;
    /**
     * The AudioDevice where audio samples are written to.
     */
    private AudioDevice device;

    private SwingWorker run;

    private int currentFrame = 0;

    private int i;

    private Song musica_remove;

    private Song musica_now;


    private ArrayList <Song> musicas = new ArrayList<Song>();

    private ArrayList<String[]> temp = new ArrayList<String[]>();

    private String[][] array = {};

    private boolean playp = true; // botao play/pause "ligado"
    private boolean playp_is_pressed = true; //botao play/pause apertado
    private boolean stop = false; // botao stop "ligado"




    private final ActionListener buttonListenerPlayNow = e -> {
        currentFrame = 0;

        i = Interface.getIdx();
        musica_now = musicas.get(i);
        playp_is_pressed = true;


        run = new SwingWorker(){
            @Override
            public Object doInBackground() throws Exception{

                Interface.setPlayingSongInfo(musica_now.getTitle(), musica_now.getAlbum(), musica_now.getArtist());

                if(bitstream != null){
                    try {
                        bitstream.close();
                    } catch (BitstreamException ex) {
                        throw new RuntimeException(ex);
                    }

                    device.close();
                }


                try {
                    device = FactoryRegistry.systemRegistry().createAudioDevice();
                    device.open(decoder = new Decoder());
                    bitstream = new Bitstream(musica_now.getBufferedInputStream());

                } catch (JavaLayerException | FileNotFoundException ex) {
                    throw new RuntimeException(ex);
                }

                while(true){
                    if (playp_is_pressed){
                        try {
                            Interface.setTime((int) (currentFrame * (int) musica_now.getMsPerFrame()), (int) musica_now.getMsLength());
                            Interface.setPlayPauseButtonIcon(1);
                            Interface.setEnabledPlayPauseButton(true);
                            Interface.setEnabledStopButton(true);
                            playp = true;
                            stop = true;

                            playNextFrame();

                        } catch (JavaLayerException ex) {
                            throw new RuntimeException(ex);
                        }
                    }

                }

            }

        };

        run.execute();

    };


    private final ActionListener buttonListenerRemove = e -> {
        i = Interface.getIdx();
        musica_remove = musicas.get(i);

        temp.remove(i);
        array = temp.toArray(new String[this.temp.size()][7]);
        Interface.setQueueList(array);
        musicas.remove(i);

        if(currentFrame != 0 && musica_now == musica_remove){
            stop();
        }
    };


    private final ActionListener buttonListenerAddSong = e -> {
        Song musica;

        //Abrir a tela
        try{
            musica = this.Interface.openFileChooser();
        }catch(IOException | InvalidDataException | BitstreamException | UnsupportedTagException ex){
            throw new RuntimeException(ex);
        }

        //Botando a música nova na tela
        temp.add(musica.getDisplayInfo());
        array = temp.toArray(new String[this.temp.size()][7]);
        Interface.setQueueList(array);

        musicas.add(musica);

    };
    private final ActionListener buttonListenerPlayPause = e -> {
        if (playp == true){
            playp_is_pressed = false;
            playp = false;
            Interface.setPlayPauseButtonIcon(0);
        }

        else{
            playp_is_pressed = true;
            playp = true;
            Interface.setPlayPauseButtonIcon(1);
        }

    };
    private final ActionListener buttonListenerStop = e -> {
        if(stop == true){
            stop();
        }
    };
    private final ActionListener buttonListenerNext = e -> {};
    private final ActionListener buttonListenerPrevious = e -> {};
    private final ActionListener buttonListenerShuffle = e -> {};
    private final ActionListener buttonListenerLoop = e -> {};
    private final MouseInputAdapter scrubberMouseInputAdapter = new MouseInputAdapter() {
        @Override
        public void mouseReleased(MouseEvent e) {
        }

        @Override
        public void mousePressed(MouseEvent e) {
        }

        @Override
        public void mouseDragged(MouseEvent e) {
        }
    };

    public Player() {
        EventQueue.invokeLater(() -> Interface = new PlayerWindow(
                ("MP3 Player"),
                array,
                buttonListenerPlayNow,
                buttonListenerRemove,
                buttonListenerAddSong,
                buttonListenerShuffle,
                buttonListenerPrevious,
                buttonListenerPlayPause,
                buttonListenerStop,
                buttonListenerNext,
                buttonListenerLoop,
                scrubberMouseInputAdapter)
        );
    }

    //<editor-fold desc="Essential">
    private void stop(){
        playp_is_pressed = false;
        Interface.setEnabledStopButton(false);
        Interface.resetMiniPlayer();
    }
    /**
     * @return False if there are no more frames to play.
     */
    private boolean playNextFrame() throws JavaLayerException {
        // TODO: Is this thread safe?
        if (device != null) {
            Header h = bitstream.readFrame();
            if (h == null) return false;

            SampleBuffer output = (SampleBuffer) decoder.decodeFrame(h, bitstream);
            device.write(output.getBuffer(), 0, output.getBufferLength());
            bitstream.closeFrame();
            currentFrame++;
        }
        return true;
    }

    /**
     * @return False if there are no more frames to skip.
     */
    private boolean skipNextFrame() throws BitstreamException {
        // TODO: Is this thread safe?
        Header h = bitstream.readFrame();
        if (h == null) return false;
        bitstream.closeFrame();
        currentFrame++;
        return true;
    }

    /**
     * Skips bitstream to the target frame if the new frame is higher than the current one.
     *
     * @param newFrame Frame to skip to.
     * @throws BitstreamException Generic Bitstream exception.
     */
    private void skipToFrame(int newFrame) throws BitstreamException {
        // TODO: Is this thread safe?
        if (newFrame > currentFrame) {
            int framesToSkip = newFrame - currentFrame;
            boolean condition = true;
            while (framesToSkip-- > 0 && condition) condition = skipNextFrame();
        }
    }
    //</editor-fold>
}
