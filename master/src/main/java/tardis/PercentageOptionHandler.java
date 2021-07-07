package tardis;

import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.FloatOptionHandler;
import org.kohsuke.args4j.spi.Setter;

/**
 * A {@link FloatOptionHandler} for percentages.
 * 
 * @author Pietro Braione
 */
public class PercentageOptionHandler extends FloatOptionHandler {

    public PercentageOptionHandler(CmdLineParser parser, OptionDef option, Setter<? super Float> setter) {
        super(parser, option, setter);
    }
    
    @Override
    protected Float parse(String argument) throws NumberFormatException {
        final float f = super.parse(argument);
        if (f < 0.0f || f > 1.0f) {
            throw new NumberFormatException();
        }
        return Float.valueOf(f);
    }

}
