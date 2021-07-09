package app.mesmedicaments.api;

public interface IConvertisseurIdentifieur<ID extends IdentifieurGenerique, S> {

    public S serializeIdentifier(ID identifier);

    public ID deserializeIdentifier(S serialized);

}
