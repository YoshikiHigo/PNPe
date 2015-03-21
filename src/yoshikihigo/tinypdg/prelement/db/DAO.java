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
import yoshikihigo.tinypdg.prelement.data.Dependence;

public class DAO {

	static public final String PROBABILITIES_SCHEMA = "id integer primary key autoincrement, type string, fromAbsoluteText string, fromRelativeText string, toText string, map string, support integer, confidence real";

	protected Connection connector;
	private PreparedStatement insertToProbabilities;
	private PreparedStatement selectFromProbabilitiesWithType;
	private PreparedStatement selectFromProbabilitiesWithoutType;

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
			statement
					.executeUpdate("create table if not exists probabilities ("
							+ PROBABILITIES_SCHEMA + ")");

			this.insertToProbabilities = this.connector
					.prepareStatement("insert into probabilities (type, fromAbsoluteText, fromRelativeText, toText, map, support, confidence) values (?, ?, ?, ?, ?, ?, ?)");
			this.selectFromProbabilitiesWithType = this.connector
					.prepareStatement("select type, fromRelativeText, toText, map, support, confidence from probabilities where (fromAbsoluteText = ?) and (type = ?)");
			this.selectFromProbabilitiesWithoutType = this.connector
					.prepareStatement("select type, fromRelativeText, toText, map, support, confidence from probabilities where fromAbsoluteText = ?");

		} catch (final SQLException e) {
			e.printStackTrace();
			System.exit(0);
		}

		this.numberInWaitingBatchForProbablities = 0;
	}

	public void addToProbabilities(final AppearanceProbability probability) {

		try {
			this.insertToProbabilities.setString(1, probability.type.text);
			this.insertToProbabilities.setString(2,
					probability.dependence.fromNodeAbsoluteNormalizationText);
			this.insertToProbabilities.setString(3,
					probability.dependence.fromNodeRelativeNormalizationText);
			this.insertToProbabilities.setString(4,
					probability.dependence.toNodeNormalizationText);
			this.insertToProbabilities.setString(5,
					probability.dependence.absoluteRelativeMap);
			this.insertToProbabilities.setInt(6, probability.support);
			this.insertToProbabilities.setFloat(7, probability.confidence);
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
			final DEPENDENCE_TYPE type, final String fromNodeAbsoluteText) {

		final List<AppearanceProbability> probabilities = new ArrayList<>();

		try {
			this.selectFromProbabilitiesWithType.clearParameters();
			this.selectFromProbabilitiesWithType.setString(1,
					fromNodeAbsoluteText);
			this.selectFromProbabilitiesWithType.setString(2, type.text);
			final ResultSet result = this.selectFromProbabilitiesWithType
					.executeQuery();

			while (result.next()) {
				final String fromNodeRelativeText = result.getString(2);
				final String toNodeText = result.getString(3);
				final String absoluteRelativeMap = result.getString(4);
				final int support = result.getInt(5);
				final float confidence = result.getFloat(6);
				final Dependence dependence = new Dependence(
						fromNodeAbsoluteText, fromNodeRelativeText, toNodeText,
						absoluteRelativeMap);
				final AppearanceProbability probability = new AppearanceProbability(
						type, dependence, confidence, support);
				probabilities.add(probability);
			}

		} catch (final SQLException e) {
			e.printStackTrace();
			System.exit(0);
		}

		return probabilities;
	}

	public List<AppearanceProbability> getAppearanceFrequencies(
			final String fromNodeAbsoluteText) {

		final List<AppearanceProbability> probabilities = new ArrayList<AppearanceProbability>();

		try {
			this.selectFromProbabilitiesWithoutType.clearParameters();
			this.selectFromProbabilitiesWithoutType.setString(1,
					fromNodeAbsoluteText);
			final ResultSet result = this.selectFromProbabilitiesWithoutType
					.executeQuery();

			while (result.next()) {
				final DEPENDENCE_TYPE type = DEPENDENCE_TYPE
						.getDEPENDENCE_TYPE(result.getString(1));
				final String fromNodeRelativeText = result.getString(2);
				final String toNodeText = result.getString(3);
				final String absoluteRelativeMap = result.getString(4);
				final int support = result.getInt(5);
				final float confidence = result.getFloat(6);
				final Dependence dependence = new Dependence(
						fromNodeAbsoluteText, fromNodeRelativeText, toNodeText,
						absoluteRelativeMap);
				final AppearanceProbability probability = new AppearanceProbability(
						type, dependence, confidence, support);
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

			if (0 < this.numberInWaitingBatchForProbablities) {
				this.insertToProbabilities.executeBatch();
				this.numberInWaitingBatchForProbablities = 0;
			}

			final Statement statement = this.connector.createStatement();
			statement
					.executeUpdate("create index index_fromNodeAbsoluteText_probabilities on probabilities(fromAbsoluteText)");
			statement
					.executeUpdate("create index index_type_fromNodeAbsoluteText_probabilities on probabilities(type,fromAbsoluteText)");

			this.insertToProbabilities.close();
			this.connector.close();

		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	}
}
