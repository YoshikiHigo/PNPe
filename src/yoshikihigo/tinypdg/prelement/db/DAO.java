package yoshikihigo.tinypdg.prelement.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import yoshikihigo.tinypdg.prelement.data.AppearanceProbability;
import yoshikihigo.tinypdg.prelement.data.DEPENDENCE_TYPE;

public class DAO {

	static public final String TEXTS_SCHEMA = "id integer primary key autoincrement, hash integer, text string";
	static public final String PROBABILITIES_SCHEMA = "id integer primary key autoincrement, type string, fromhash integer, tohash integer, support integer, confidence real";

	protected Connection connector;
	private PreparedStatement insertToTexts;
	private PreparedStatement insertToProbabilities;
	private PreparedStatement selectFromProbabilitiesWithType;
	private PreparedStatement selectFromProbabilitiesWithoutType;

	private int numberInWaitingBatchForTexts;
	private int numberInWaitingBatchForProbablities;

	public DAO(final String database) {

		try {
			Class.forName("org.sqlite.JDBC");
		} catch (final ClassNotFoundException e) {
			e.printStackTrace();
			System.exit(0);
		}

		try {
			final StringBuilder url = new StringBuilder();
			url.append("jdbc:sqlite:");
			url.append(database);
			this.connector = DriverManager.getConnection(url.toString());

			final Statement statement = this.connector.createStatement();
			statement.executeUpdate("create table if not exists texts ("
					+ TEXTS_SCHEMA + ")");
			statement
					.executeUpdate("create table if not exists probabilities ("
							+ PROBABILITIES_SCHEMA + ")");

			this.insertToTexts = this.connector
					.prepareStatement("insert into texts (hash, text) values (?, ?)");
			this.insertToProbabilities = this.connector
					.prepareStatement("insert into probabilities (type, fromhash, tohash, support, confidence) values (?, ?, ?, ?, ?)");
			this.selectFromProbabilitiesWithType = this.connector
					.prepareStatement("select tohash, (select text from texts T where T.hash = F.tohash), type, support, confidence from probabilities F where (fromhash = ?) and (type = ?)");
			this.selectFromProbabilitiesWithoutType = this.connector
					.prepareStatement("select tohash, (select text from texts T where T.hash = F.tohash), type, support, confidence from probabilities F where fromhash = ?");

		} catch (final SQLException e) {
			e.printStackTrace();
			System.exit(0);
		}

		this.numberInWaitingBatchForTexts = 0;
		this.numberInWaitingBatchForProbablities = 0;
	}

	public void addToTexts(final int hash, final String text) {

		try {
			this.insertToTexts.setInt(1, hash);
			this.insertToTexts.setString(2, text);
			this.insertToTexts.addBatch();

			if (2000 < ++this.numberInWaitingBatchForTexts) {
				this.insertToTexts.executeBatch();
				this.numberInWaitingBatchForTexts = 0;
			}

		} catch (final SQLException e) {
			e.printStackTrace();
			System.exit(0);
		}
	}

	public void addToProbabilities(final DEPENDENCE_TYPE type,
			final int fromhash, final AppearanceProbability probability) {

		try {
			this.insertToProbabilities.setString(1, type.text);
			this.insertToProbabilities.setInt(2, fromhash);
			this.insertToProbabilities.setInt(3, probability.hash);
			this.insertToProbabilities.setInt(4, probability.support);
			this.insertToProbabilities.setFloat(5, probability.confidence);
			this.insertToProbabilities.addBatch();

			if (2000 < ++this.numberInWaitingBatchForProbablities) {
				this.insertToProbabilities.executeBatch();
				this.numberInWaitingBatchForProbablities = 0;
			}

		} catch (final SQLException e) {
			e.printStackTrace();
			System.exit(0);
		}
	}

	public List<AppearanceProbability> getAppearanceProbabilities(
			final DEPENDENCE_TYPE type, final int fromhash) {

		final List<AppearanceProbability> probabilities = new ArrayList<>();

		try {
			this.selectFromProbabilitiesWithType.clearParameters();
			this.selectFromProbabilitiesWithType.setInt(1, fromhash);
			this.selectFromProbabilitiesWithType.setString(2, type.text);
			final ResultSet result = this.selectFromProbabilitiesWithType
					.executeQuery();

			while (result.next()) {
				final int tohash = result.getInt(1);
				final String toText = result.getString(2);
				final int support = result.getInt(4);
				final float confidence = result.getFloat(5);
				final AppearanceProbability probability = new AppearanceProbability(
						type, confidence, support, tohash, toText);
				probabilities.add(probability);
			}

		} catch (final SQLException e) {
			e.printStackTrace();
			System.exit(0);
		}

		return probabilities;
	}

	public List<AppearanceProbability> getAppearanceFrequencies(
			final int fromhash) {

		final List<AppearanceProbability> probabilities = new ArrayList<AppearanceProbability>();

		try {
			this.selectFromProbabilitiesWithoutType.clearParameters();
			this.selectFromProbabilitiesWithoutType.setInt(1, fromhash);
			final ResultSet result = this.selectFromProbabilitiesWithoutType
					.executeQuery();

			while (result.next()) {
				final int tohash = result.getInt(1);
				final String toText = result.getString(2);
				final DEPENDENCE_TYPE type = DEPENDENCE_TYPE
						.getDEPENDENCE_TYPE(result.getString(3));
				final int support = result.getInt(4);
				final float confidence = result.getFloat(5);
				final AppearanceProbability probability = new AppearanceProbability(
						type, confidence, support, tohash, toText);
				probabilities.add(probability);
			}

		} catch (final SQLException e) {
			e.printStackTrace();
			System.exit(0);
		}

		return probabilities;
	}

	public void close() {

		try {

			if (0 < this.numberInWaitingBatchForTexts) {
				this.insertToTexts.executeBatch();
				this.numberInWaitingBatchForTexts = 0;
			}

			if (0 < this.numberInWaitingBatchForProbablities) {
				this.insertToProbabilities.executeBatch();
				this.numberInWaitingBatchForProbablities = 0;
			}

			this.insertToTexts.close();
			this.insertToProbabilities.close();
			this.connector.close();

		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	}
}
