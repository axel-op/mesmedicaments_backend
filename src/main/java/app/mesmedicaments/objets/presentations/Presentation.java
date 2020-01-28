package app.mesmedicaments.objets.presentations;

import org.json.JSONString;

import app.mesmedicaments.IJSONSerializable;
import app.mesmedicaments.objets.Pays;

public abstract class Presentation<P extends Pays> implements IJSONSerializable, JSONString {}